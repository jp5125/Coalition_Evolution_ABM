package baboons;

import java.awt.Color;
import sim.engine.*;
import sim.portrayal.simple.OvalPortrayal2D;
import sim.util.Bag;
import sim.util.*;
import sweep.GUIStateSweep;
import java.util.Comparator;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Random;
import java.util.*;

public class Group implements Steppable
{
	int x; //x-location
	int y; //y-location
	Bag members = null; //bag of baboons who are members
	public Stoppable event; //so the group can be removed from the schedule
	Environment state;
	ArrayList<Baboon> fertileFemales = new ArrayList<>();
	ArrayList <Baboon> consortMales = new ArrayList<>();
	Bag coalitionMales = new Bag();
	
	//group constructor
	public Group(Environment state, int x, int y, Bag members)
	{
		super();
		this.state = state;
		this.x = x;
		this.y = y;
		this.members = members;
		
		for(int i = 0; i<this.members.numObjs; i++)
		{
			Baboon b = (Baboon)this.members.objs[i];
			b.setGroup(this);
		}
	}
	
	public Group(Environment state) //specifically for group.fission()
	{
		super();
		this.state = state;
		this.members = new Bag();
	}
	
	//utility method to calculate the proportion of coalition gene carrying males to males without the gene. Used for visualization
	public double getAdultMaleCoalitionFrequency()
	{
		int adultMales= 0; //number of adult males in this group
		int carriers = 0; //number of males carrying the coaliton gene
		
		for(int i = 0; i < members.numObjs; i++)
		{
			Baboon b = (Baboon) members.objs[i];
			if(b.isMale() && !b.isJuvenile)
			{
				adultMales++;
				if(b.hasCoalitionGene)
				{
					carriers++;
				}
			}
		}
		if(adultMales == 0)
		{
			return 0.0;
		}
		else
		{
			return (carriers * 1.0 / adultMales);
		}
		
	}
	
	//initial dominance hierarchy implementation, unsure of hidden bugs due to redrawing from a bag so this method was re-implemented using an ArrayList below
	public void updateDominanceHierarchyBag() 
	{
		//First, we gather the male baboons from the set of all group members
		Bag males = new Bag(); //initialize an empty bag for males
		for(int i = 0; i < members.numObjs; i++) //for each agent in the group (object in the members bag)
		{
			Baboon b = (Baboon)members.objs[i]; //baboon b is a an object in the members bag cast as a baboon instance
			if (b.isMale() && !b.isJuvenile) //if the boolean state variable "Male" for b equals TRUE
			{
				males.add(b); //add that individual to the new bag "males"
			}
		}
		
		/*
		 * Next, we must determine fighting ability based on age, inspired by Noe 1992 and Noe 1994.
		 * Fighting ability is a signifcant determinant of dominance, and in male baboons fighting ability is mostly determined by age 
		 * age -> fighting ability -> dominance ranking
		*/
		
		for(int i = 0; i < males.numObjs; i++)
		{
			Baboon male = (Baboon)males.objs[i]; //***note for next week lab, is it redundant to recast this as a baboon as males should already contain objects of class baboon?***
			
			// Function to determine each males fighting ability based on age
			male.fightingAbility = male.calculateFightingAbility();
			
		}
		
		//From here, we can sort the males by fighting ability. This essentially creates the dominance hierarchy.
		males.sort(new Comparator()
				{
					public int compare(Object a, Object b)
					{
						Baboon maleA = (Baboon) a;
						Baboon maleB = (Baboon) b;
						return Double.compare(maleB.fightingAbility, maleA.fightingAbility);
					}
				});
		
		//Finally, we can update the dominance rank for each male
		for (int rank = 0; rank < males.numObjs; rank++) {
	        Baboon male = (Baboon) males.objs[rank];
	        male.dominanceRank = rank + 1; // Rank starts at 1 for highest-ranking male
	    } 
    }
	

	//Uses an ArrayList to keep track of the dominance hierarchy instead of a bag to avoid ordering errors with repeatedy drawing from a bag
	public void updateDominanceHierarchyArray()
	{
		//Collect all male baboons from the members bag and put into ArrayList
		ArrayList<Baboon> males = new ArrayList<>();
		for(int i = 0; i < members.numObjs; i++)
		{
			Baboon b = (Baboon)members.objs[i];
			if(b.isMale() && !b.isJuvenile )
			{
				males.add(b);
			}
		}
		
		//reset all ranks for adult males in the group
		for(int i = 0; i < males.size(); i++)
		{
			Baboon b = males.get(i);
			b.dominanceRank = -1;
		}
		
		//Next, calculate fighting ability based on age for each male
		for(Baboon male : males)
		{
			male.fightingAbility = male.calculateFightingAbilityLogistic();
		}
		
		//Third, sort males by fighting ability, creating a dominance hierarchy
		Collections.sort(males, new Comparator<Baboon>()
		{
			public int compare(Baboon b1, Baboon b2)
			{
				return Double.compare(b2.fightingAbility, b1.fightingAbility);
			}
		});
		
		//Finally, assign dominance ranks based on sorted order
		for(int rank = 0; rank < males.size(); rank++)
		{
			Baboon male = males.get(rank);
			male.dominanceRank = rank + 1; //rank 1 should be highest dominance rank
		}
	}

	public void coalitionGame()
	{
		//Clear previous lists
		fertileFemales.clear();
		consortMales.clear();
		
		//temporary tracking sets for only 1 challenge per timestep 
		HashSet<Baboon> malesAlreadyUsedThisStep = new HashSet<>();
		HashSet<Baboon> femalesAlreadyChallengedThisStep = new HashSet<>();
		

		//Identify fertile females in the group
		ArrayList<Baboon> sortedFemales = new ArrayList<>(); //create an empty ArrayList to store fertile females in
		for(Object obj : members) //for each baboon in the group's members bag
		{
			Baboon b = (Baboon) obj; //cast each object in the bag as type baboon and assign it as a baboon object "b"
			if(!b.isMale() && !b.isJuvenile && b.cycleDay >= 27 && b.cycleDay <= 33) //If the Baboon object from the members bag has a state value of "male" = false, isJuvenile = false, and is between cycle days 27 and 33, add to fertile array
			{
				sortedFemales.add(b);
			}
		}

		//Next, we sort fertile females by closeness to peak fertility (day 30 optimal)
		//Use a comparator to compare the absolute difference between each pair of females in the sortedFemales ArrayList
		sortedFemales.sort(new Comparator<Baboon>()
		{
			@Override
			public int compare(Baboon female1, Baboon female2)
			{
				int diff1 = Math.abs(female1.cycleDay - 30);
				int diff2 = Math.abs(female2.cycleDay - 30);
				return Integer.compare(diff1, diff2);
			}

		});

		fertileFemales.addAll(sortedFemales); //Add all sorted females to the fertileFemales list

		//Identify male baboons in group
		ArrayList<Baboon> sortedMales = new ArrayList<>();
		for(Object obj : members)
		{
			Baboon b = (Baboon) obj;
			if(b.isMale() && !b.isJuvenile)
			{
				sortedMales.add(b);
			}
		}

		//Sort males by dominance rank in asceending order
		sortedMales.sort(new Comparator<Baboon>()
		{
			@Override
			public int compare(Baboon m1, Baboon m2)
			{
				return Integer.compare(m1.dominanceRank, m2.dominanceRank);
			}
		});

		//Now we can assign initial consortships
		int maleIndex = 0;
		for(Baboon female : fertileFemales) //for each female in the fertileFemales ArrayList
		{
			if(maleIndex >= sortedMales.size()) //case where no more males are available to pair with
			{
				break;
			}

			Baboon consortMale = sortedMales.get(maleIndex);
			maleIndex++;

			consortMales.add(consortMale); //Add to consort males list
			female.currentConsortMale = consortMale; //Set the female's state variable for 'currentConsortmale' to the current
			female.recordMating(consortMale); //record the mating event for the initial consortship

		}

		//Identify males eligible to form a coalition
		ArrayList<Baboon> coalitionaryMales = new ArrayList<>();

		for(Baboon male : sortedMales) //for each male in the sorted list of males by rank
		{
			if(maleAttemptsCoalition(male, state))
			{
				coalitionaryMales.add(male);
			}
		}

		//randomly pair males in coalitionaryMales into coalitions (add more detail in later model iteration for strategic pairing)
		ArrayList<ArrayList<Baboon>> coalitions = formCoalitions(coalitionaryMales);

		for(ArrayList<Baboon> coalition : coalitions)
		{
			if(fertileFemales.isEmpty()) continue;
			
			Baboon male1 = coalition.get(0);
			Baboon male2 = coalition.get(1);
			
			/*
			 * The logic here allows for oneChallengePerTimestep to be switched on and off, and 
			 * depending on the state of oneChallengePerTimestep, different logic will run
			 */
			if(state.oneChallengePerTimestep == true && (malesAlreadyUsedThisStep.contains(male1) || malesAlreadyUsedThisStep.contains(male2))) 
			{
				continue;
			}
			
			Baboon targetFemale = null;
			
			if(state.oneChallengePerTimestep)
			{
				ArrayList<Baboon> availableFemales = new ArrayList<>();
				
				for(Baboon female : fertileFemales)
				{
					if(!femalesAlreadyChallengedThisStep.contains(female))
					{
						availableFemales.add(female);
					}
				}
				
				/*
				 * when oneChallengePerTimestep == true, If every fertile female has been challenged this timestep
				 * then this coalition does not get a target
				 */
				if(availableFemales.isEmpty())
				{
					continue;
				}
				
				targetFemale = availableFemales.get(state.random.nextInt(availableFemales.size()));
			}
			else //original logic 
			{
				targetFemale = fertileFemales.get(state.random.nextInt(fertileFemales.size()));
			}
			
			Baboon currentConsort = targetFemale.currentConsortMale;
			
			ChallengeOutcome outcome = resolveCoalitionChallenge(male1, male2, targetFemale, currentConsort, state);
			applyAlternativeTactics(outcome, state);
			
			//when oneChallenge is switched on, mark males and females as used
			if(state.oneChallengePerTimestep == true)
			{
				femalesAlreadyChallengedThisStep.add(targetFemale);
				malesAlreadyUsedThisStep.add(male1);
				malesAlreadyUsedThisStep.add(male2);
				
			}
		}

		//per-day mating record updating, ensures males who are consorts for multiple days in a row get more than 1 mating record for their consortship
		for(Baboon female : fertileFemales)
		{
			Baboon consort = female.currentConsortMale;
			if(consort != null)
			{
				female.recordMating(consort);
			}
		}

	}

	// Utility method to form coalitions(randomly)
	public ArrayList<ArrayList<Baboon>> formCoalitions(ArrayList<Baboon> eligibleMales)
	{
	    ArrayList<ArrayList<Baboon>> coalitions = new ArrayList<>();

	    // Shuffle to make random pairs
	    Collections.shuffle(eligibleMales, new Random(state.random.nextLong()));

	    // Pair them
	    for (int i = 0; i < eligibleMales.size() - 1; i += 2)
	    {
	        ArrayList<Baboon> pair = new ArrayList<>();
	        pair.add(eligibleMales.get(i));
	        pair.add(eligibleMales.get(i + 1));
	        coalitions.add(pair);
	    }

	    return coalitions;
	}

	private boolean maleAttemptsCoalition(Baboon male, Environment state)
	{
		//if the agent is not male or is not an adult, return false
		if(male == null || !male.isMale() || male.isJuvenile)
		{
			return false;
		}
		
		//if the male is a consort male, he is not eligible to join a coalition, thus return false
		if(consortMales.contains(male))
		{
			return false;
		}
		
		//switch statement to determine which information is used in the coalition participation decision making
		switch(state.participationMode)
		{
		//baseline case: 
		case BASELINE:
			return male.hasCoalitionGene && (male.getLifeStage() == LifeStage.POST_PRIME || male.getLifeStage() == LifeStage.SENESCENT);
		
		//state-dependent threshold case:
		case STATE_THRESHOLD:
			if(state.requireCoalitionGeneForStateDependentMode && !male.hasCoalitionGene)
			{
				return false;
			}
			return male.getLifeStage() == LifeStage.POST_PRIME
				|| male.getLifeStage() == LifeStage.SENESCENT
				|| male.calculateFightingAbilityLogistic() < state.coalitionFaThreshold
				|| male.dominanceRank > state.coalitionRankThreshold;
		
		//expected value of coalition case:
		case STATE_EV:
			//return false if male does not have coalition gene
			if(state.requireCoalitionGeneForStateDependentMode && !male.hasCoalitionGene)
			{
				return false;
			}
			
			//initialize focal male and comparison variables
			double maleFA = male.calculateFightingAbilityLogistic();
			
			double partnerFASum = 0.0;
			int partnerCount = 0;
			double bestConsortFA = 0.0;

			//scan through all group members
			for(Object obj : members)
			{
				Baboon other = (Baboon) obj;
				if(!other.isMale() || other.isJuvenile)
				{
					continue;
				}
				
				
				//calculate each other male's FA
				double otherFA = other.calculateFightingAbilityLogistic();
				if(consortMales.contains(other))
				{
					bestConsortFA = Math.max(bestConsortFA, otherFA);
				}
				
				else if(other != male)
				{
					boolean partnerEligibleByGene = !state.requireCoalitionGeneForStateDependentMode || other.hasCoalitionGene;
					if(partnerEligibleByGene)
					{
						partnerFASum += otherFA;
						partnerCount++;
					}
				}
			}
			
			// If no eligible partner exists, coalition cannot form
		    if(partnerCount == 0)
		    {
		        return false;
		    }

		    // Expected partner quality under random pairing
		    double expectedPartnerFA = partnerFASum / partnerCount;

			//estimate coalition success probability
			double pWin = 0.5;
			if(bestConsortFA > 0.0)
			{
				double coalitionStrength = maleFA + expectedPartnerFA;
				double consortStrength = bestConsortFA * state.coalitionDefenseBonus;
				pWin = 1.0 / (1.0 + Math.exp(-state.coalitionWinBeta * (coalitionStrength - consortStrength))); //logistic win function
			}

			double mortalityRisk = state.probMortalWoundCoalition;
			double residualFitnessTerm = Math.max(0.0, 1.0 - ((double) male.age / Math.max(1.0, (double) male.maxAge))); //function that estimates future reproductive value remaining
			
			//formula that calculates the expected value of the coalition
			/*
			 * pWin * state.coalitionBenefit term is the expected reward from the coalition
			 * - state.coalitionChallengeCost term is the cost of attempting a coalition
			 * - mortalityRisk * state.futureFitnessWeight * residualFitnessWeight term is a penalization for coalitions being dangerous
			 * 
			 */
			double evCoalition = pWin * state.coalitionBenefit - state.coalitionChallengeCost - mortalityRisk * state.futureFitnessWeight * residualFitnessTerm;
			
			//payoff for doing nothing 
			double evNonCoalition = state.baselineMatingBenefit;
			return evCoalition > evNonCoalition;
		default:
			return false;
		}
	}

	//new method for resolving a coalition challenge, stores all relevant data in an instance of the ChallengeOutcome class
	private ChallengeOutcome resolveCoalitionChallenge(
		Baboon male1,
		Baboon male2,
		Baboon targetFemale,
		Baboon currentConsort,
		Environment state
	)
	{
		ChallengeOutcome outcome = new ChallengeOutcome();
		outcome.targetFemale = targetFemale;
		outcome.oldConsort = currentConsort;
		outcome.coalitionMale1 = male1;
		outcome.coalitionMale2 = male2;

		if(targetFemale == null)
		{
			return outcome;
		}
		
		//if there is not consort male currently
		if(currentConsort == null)
		{
			outcome.coalitionSucceeded = true;
			outcome.disruptionOccurred = true;
			outcome.pWin = 1.0;
			outcome.newConsort = chooseNewConsortAfterCoalitionWin(male1, male2, state);
			targetFemale.currentConsortMale = outcome.newConsort;
			consortMales.add(outcome.newConsort);
			targetFemale.recordMating(outcome.newConsort);
			return outcome;
		}
		
		//in all other cases where there is a consort male and a coalition challenge has been initiated, coalition success is determined under different conditions
		switch(state.successMode)
		{
		//original 50/50 chance of coalition success
		case COIN_FLIP:
			outcome.pWin = 0.5;
			outcome.coalitionSucceeded = state.random.nextBoolean();
			outcome.disruptionOccurred = outcome.coalitionSucceeded;
			break;
		
		//uses FA in order todetermine the likelihood of the coalition beating the consort
		case FA_LOGISTIC:
		{
			double fa1 = male1.calculateFightingAbilityLogistic();
			double fa2 = male2.calculateFightingAbilityLogistic();
			double consortFA = currentConsort.calculateFightingAbilityLogistic();
			double coalitionStrength = fa1 + fa2;
			double consortStrength = consortFA;
			
			//calculates the probability the coalition wins, tuned by Beta parameter. Higher beta = more deterministic, lower beta is noisier
			outcome.pWin = 1.0 / (1.0 + Math.exp(-state.coalitionWinBeta * (coalitionStrength - consortStrength)));
			outcome.coalitionSucceeded = state.random.nextDouble() < outcome.pWin;
			outcome.disruptionOccurred = outcome.coalitionSucceeded;
			break;
		}
		
		/*
		 * This is a more general case where the coalition attempts to disrupt the consort male's defense of the female. 
		 * Rather than just calculate if the coalition beats the consort and takes over, this code models whether the male can disrupt the consort male
		 * and take the female. It models the harrassment/herding/interference dynamics of the system. 
		 * 
		 * There are 3 outcomes in this case, rather than just coalition success or failure.
		 * Coalition fails to disrupt consort -> consort male retains female
		 * Coalition disrupts consort male -> consort male still retains female (consort male successfully herded female)
		 * Coalition disrupts consort male -> coalition succeeds and one coalition male becomes new consort 
		 */
		
		case FA_LOGISTIC_HERDING:
		{
			double fa1 = male1.calculateFightingAbilityLogistic();
			double fa2 = male2.calculateFightingAbilityLogistic();
			double consortFA = currentConsort.calculateFightingAbilityLogistic();
			double coalitionStrength = fa1 + fa2;
			double consortStrength = consortFA;
			
			//we add pDisruption here, which is the probability the coalition successfully disrupts the consortship. Same is pWin in FA_LOGISTIC case
			//but is used to calculate a more complex pWin below
			double pDisruption = 1.0 / (1.0 + Math.exp(-state.coalitionWinBeta * (coalitionStrength - consortStrength)));
			double strengthTotal = coalitionStrength + consortStrength;
			
			//the probability the consort male retains the female is consortStrength / strengthTotal if strength > 0.0, else it is just 0.5
			double pRetain = strengthTotal > 0.0 ? consortStrength / strengthTotal : 0.5;
			//probability coalition is successful 
			outcome.pWin = pDisruption * (1.0 - pRetain);
			outcome.disruptionOccurred = state.random.nextDouble() < pDisruption;
			if(outcome.disruptionOccurred)
			{
				outcome.consortRetainedFemale = state.random.nextDouble() < pRetain;
				outcome.coalitionSucceeded = !outcome.consortRetainedFemale;
			}
			else
			{
				outcome.consortRetainedFemale = true;
				outcome.coalitionSucceeded = false;
			}
			break;
		}
		
		//default case is essentially the same as COIN_FLIP
		default:
			outcome.pWin = 0.5;
			outcome.coalitionSucceeded = state.random.nextBoolean();
			outcome.disruptionOccurred = outcome.coalitionSucceeded;
			break;
		}
		
		//if the coalition is successful, remove the determine new consort male, remove old consort, recordMating with new consort
		if(outcome.coalitionSucceeded)
		{
			outcome.newConsort = chooseNewConsortAfterCoalitionWin(male1, male2, state);
			targetFemale.currentConsortMale = outcome.newConsort;
			consortMales.remove(currentConsort);
			consortMales.add(outcome.newConsort);
			targetFemale.recordMating(outcome.newConsort);
		}
		
		male1.lifetimeCoalitionTracker ++;
		male2.lifetimeCoalitionTracker ++;
		mortalWoundConsort(state, currentConsort);
		mortalWoundCoalition(state, male1);
		mortalWoundCoalition(state, male2);
		return outcome;
	}

	private void applyAlternativeTactics(ChallengeOutcome outcome, Environment state)
	{
		if(outcome == null || outcome.targetFemale == null)
		{
			return;
		}

		switch(state.alternativeMode)
		{
		case NONE:
			return;
		case SNEAKER:
			if(!state.disruptionEnablesSneaking || !outcome.disruptionOccurred)
			{
				return;
			}

			ArrayList<Baboon> sneakerMales = new ArrayList<>();
			for(Object obj : members)
			{
				Baboon male = (Baboon) obj;
				if(!male.isMale() || male.isJuvenile || !male.hasSneakerTrait)
				{
					continue;
				}
				if(male == outcome.coalitionMale1 || male == outcome.coalitionMale2)
				{
					continue;
				}
				if(male == outcome.oldConsort || male == outcome.newConsort)
				{
					continue;
				}
				sneakerMales.add(male);
			}

			if(sneakerMales.isEmpty())
			{
				return;
			}

			Baboon sneaker = sneakerMales.get(state.random.nextInt(sneakerMales.size()));
			if(state.random.nextDouble() < state.sneakerSuccessProbability)
			{
				outcome.targetFemale.recordMating(sneaker);
				outcome.sneakerMatingOccurred = true;
			}
			return;
		default:
			return;
		}
	}

	private Baboon chooseNewConsortAfterCoalitionWin(Baboon male1, Baboon male2, Environment env)
	{
		if(env.random.nextBoolean())
		{
			return male1;
		}
		return male2;
	}

//utility method for cost to fighting
	public void applyFightingCost(Baboon male, double fightingCost)
	{
		fightingCost = state.cost; 
		male.fightingAbility = Math.max(0, male.fightingAbility - fightingCost); 
	}
	
	public void mortalWoundConsort(Environment state, Baboon male)
	{
		double probMortalWound = state.probMortalWoundConsort;
		
		if(state.random.nextDouble() < probMortalWound)
		{
			male.die(state);
		}
	}
	
	public void mortalWoundCoalition(Environment state, Baboon male)
	{
		double probMortalWound = state.probMortalWoundCoalition;
		
		if(state.random.nextDouble() < probMortalWound)
		{
			male.die(state);
		}
	}
	
	//group dispersion utility method
	public void groupDisperse(Environment state) 
	{
	    if (members.numObjs < state.minGroupSize) 
	    {
	        Group newGroup = state.findGroupNearest(this.x, this.y, state.sparseSpace.TOROIDAL);
	        if (newGroup == null || newGroup.members.numObjs >= state.maxGroupSize) 
	        	return;

	        for (int i = 0; i < members.numObjs; i++) 
	        {
	            Baboon b = (Baboon) members.objs[i];
	            b.x = newGroup.x;
	            b.y = newGroup.y;
	            b.setGroup(newGroup);
	            newGroup.members.add(b);
	            newGroup.updateDominanceHierarchyArray();
	        }

	        members.clear();
	        state.sparseSpace.remove(this);
	        event.stop();
	        System.out.println("Group dispersed from (" + x + ", " + y + ") to (" + newGroup.x + ", " + newGroup.y + ")");
	    }
	}
	
	public void fissionUpdated(Environment state)
	{
		double now = state.schedule.getTime();
		if(members.numObjs < state.maxGroupSize) return;
		
		//First, we create a new daughter group near the current group's location (same logic as original fission() method)
		
		Group newGroup = new Group(state); //generate a new empty group
		Int2D currentLocation = state.sparseSpace.getObjectLocation(this); //the new group's location is the same as the group that runs the group.fission()
		int dx = state.random.nextInt(5) - 2; //-2 to +2 for new x-axis value
		int dy = state.random.nextInt(5) - 2; //same for y-axis
		Int2D newLocation = new Int2D(
				Math.min(state.gridWidth - 1, Math.max(0, currentLocation.x + dx)),
				Math.min(state.gridHeight - 1, Math.max(0, currentLocation.y + dy))
				); //determines new location for the new group
		state.sparseSpace.setObjectLocation(newGroup, newLocation); //then sets new groups new location
		newGroup.x = newLocation.x;
		newGroup.y = newLocation.y;
		newGroup.event = state.schedule.scheduleRepeating(now + state.scheduleTimeInterval, 1, newGroup, state.scheduleTimeInterval); //adds the new group to the schedule
		
		//now, let's partition members by category. The only individuals who preferentially migrate now are juveniles, who go to their mom's group
		
		Bag adultFemales = new Bag();
		Bag adultMales = new Bag();
		Bag juveniles = new Bag();
		
		for(int i = 0; i < members.numObjs; i++)
		{
			Baboon b = (Baboon) members.objs[i];
			if (b.isJuvenile)
			{
				juveniles.add(b);
			}
			else if(b.isMale())
			{
				adultMales.add(b);
			}
			else
			{
				adultFemales.add(b);
			}
		}
		
		//Next, we randomly split Adult Females between the old and new group
		adultFemales.shuffle(state.random);
		int femSplit = adultFemales.numObjs / 2;
		for(int i = 0; i < adultFemales.numObjs; i++)
		{
			Baboon f = (Baboon) adultFemales.objs[i];
			Group target = (i < femSplit) ? newGroup : this;
			if(target != this)
			{
				members.remove(f);
				f.setGroup(target);
				target.members.add(f);
				f.x = target.x;
				f.y = target.y;
				if(f.event == null)
				{
					f.event = state.schedule.scheduleRepeating(now + state.scheduleTimeInterval, 0, f, state.scheduleTimeInterval);
				}
			}
		}
		
		//Same procedure as female splitting but now for adult males
		adultMales.shuffle(state.random);
	    int maleSplit = adultMales.numObjs / 2;
	    for (int i = 0; i < adultMales.numObjs; i++) 
	    {
	        Baboon m = (Baboon) adultMales.objs[i];
	        Group target = (i < maleSplit) ? newGroup : this;
	        if (target != this) 
	        {
	            members.remove(m);
	            target.members.add(m);
	            m.setGroup(target);
	            m.x = target.x; m.y = target.y;
	            if (m.event == null)
	            {
	                m.event = state.schedule.scheduleRepeating(now + state.scheduleTimeInterval, 0, m, state.scheduleTimeInterval);
	            }
	        }
	    }
	    
	    //Now we move juveniles into the group their mother joined. 99% of the time, juveniles will have mothers but if their mother dies before they mature
	    //Or they are added to a group at simulation genesis without an adult female to be their mother, we will just have them randomly join a new group
	    
	    juveniles.shuffle(state.random);
	    for (int i = 0; i < juveniles.numObjs; i++) 
	    {
	        Baboon j = (Baboon) juveniles.objs[i];

	        // Mother's destination group if available (after adults have been moved)
	        Group motherGroup = (j.mother != null) ? j.mother.group : null;

	        // Choose target: follow mom if possible; otherwise random split
	        Group target = (motherGroup != null) ? motherGroup : (state.random.nextBoolean() ? newGroup : this);

	        // Fallback if the chosen group is full (or null for any reason)
	        if (target == null || (target.members != null && target.members.numObjs >= state.maxGroupSize)) 
	        {
	            target = (target == this) ? newGroup : this;
	        }

	        // Move between groups if needed
	        if (target != this) 
	        {
	            members.remove(j);
	            target.members.add(j);
	            j.setGroup(target);
	        }

	        // Sync spatial coordinates to the target group's location
	        j.x = target.x;
	        j.y = target.y;

	        // Ensure juvenile is scheduled
	        if (j.event == null) 
	        {
	            j.event = state.schedule.scheduleRepeating(now + state.scheduleTimeInterval, 0, j, state.scheduleTimeInterval);
	        }
	    }
	    
	    //finally, we re-calculate the male dominance hierarchies of the old and new groups
	    this.updateDominanceHierarchyArray();
	    newGroup.updateDominanceHierarchyArray();
		
	}
	
	
	/*
	 * Color-gradient for proportion of cooperators in a given group
	 * 
	 * 0.0 = Red
	 * 0.25 = Orange
	 * 0.5 = Yellow
	 * 0.75 = Yellow-Green
	 * 1.0 = Green
	 */
	public void groupColor(Environment state)
	{
		
		//first, we need to collect all the adult males in the group
		int malesWithGene = 0;
		int malesWithoutGene = 0;
		
		
		//then we separate by whether or not they have the coalition gene
		for(int i = 0; i < members.numObjs;i++)
		{
			Baboon b = (Baboon) members.objs[i]; 
			
			if(b.isMale() && !b.isJuvenile)
			{
				if(b.hasCoalitionGene)
				{
					malesWithGene++;
				}
				else
				{
					malesWithoutGene++;
				}
			}	
		}
		
		//calculate the frequency of the coalition gene holders and non gene holders
		int totalMales = malesWithGene + malesWithoutGene;
		
		if(totalMales == 0)
		{
			return;
		}
		float proportionCooperator = (float) malesWithGene / (float) totalMales;
		proportionCooperator = Math.max(0.0f, Math.min(1.0f, proportionCooperator)); 
		
		//Use this to color the groups. 
		float red = Math.min(1.0f, 2.0f * (1-proportionCooperator));
		float green = Math.min(1.0f, 2.0f * proportionCooperator);
		float blue =  0.0f;
		float opacity = (float) 1.0;
		Color c = new Color(red,green,blue,opacity);
		OvalPortrayal2D o = new OvalPortrayal2D(c);
		GUIStateSweep guiState = (GUIStateSweep)state.gui;
		guiState.agentsPortrayalSparseGrid.setPortrayalForObject(this, o);
		
	}
	
	
	//remove group from simulation
	public boolean die(Environment state)
	{
		if(members == null || members.numObjs == 0)
		{
			state.sparseSpace.remove(this);
			event.stop();
			return true;
		}
		return false;
	}
	
	
	
	public void step(SimState state)
	{
		Environment eState = (Environment)state;
		if(die(eState))
			return;
		groupDisperse(eState);
		fissionUpdated(eState);
		updateDominanceHierarchyArray();
		groupColor(eState);
		coalitionGame();
	}
}