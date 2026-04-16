package baboons;
import sim.engine.SimState;
import sim.engine.Steppable;
import sim.engine.Stoppable;
import sim.util.Bag;
import java.util.HashMap;
import ec.util.MersenneTwisterFast;

public class Baboon implements Steppable
{
	
	int age; //current age of the agent in days
	int maxAge; //Lifespan in days
	boolean male; //sex of agent
	public Group group; //the group that the agent is currently a member of
	int x; //x-axis location of agent
	int y; //y-axis location of agent
	boolean isJuvenile; // flag for determining if an agent cycles or plays coalition game or not
	public boolean alive = true;
	
	
	//variables used for calculations
	Environment state;
	public Stoppable event;
	
	
	//Reproduction instance variables(for females only)
	int cycleDay = 1; //tracks current day of 33-day reproductive cycle
	int gestationRemaining = 0; //counts down the gestation period (300 days) if pregnant
	HashMap<Baboon, Integer> matingHistory; //Records mating events during the fertile period
	public Baboon currentConsortMale = null; //sets the current consort male to a fertile female 
	private static int infantDied = 0;
	private static int infantSurvived = 0;
	public String matrilineID = null;
	
	//Variables for males
	double fightingAbility; //double between 1.0 and 0.0, drops as males age
	int dominanceRank; //calculated based on comparisons of fighting abilities of other males within a group
	boolean hasCoalitionGene; //whether or not a male has the coalition gene
	public boolean hasSneakerTrait = false;
	boolean fatherHasCoalitionGene; //whether or not a newborn male's father had the coalition gene
	public int offspringCount = 0; //tracks number of offspring a male sires throughout his lifetime
	public int primeOffspring = 0; //tracks number of offspring sired when male is in prime life-history stage
	public int postPrimeOffspring = 0; //tracks number of offspring sired when male is post-prime
	public int senescentOffspring = 0; //tracks number of offspring sired when male is senescent
	private static int nextID = 0;
	public final int ID;
	public Baboon mother;
	int lifetimeCoalitionTracker;
	
	
	public Baboon(Environment state, boolean male, int x, int y, int initialAgeDays, boolean isJuvenile)
	{
		this.state = state;
		this.male = male;
		this.age = initialAgeDays;
		this.x = x;
		this.y = y;
		this.isJuvenile = isJuvenile;
		this.hasCoalitionGene = false;
		this.fatherHasCoalitionGene = false;
		this.ID = nextID++;
		this.lifetimeCoalitionTracker = 0;
		
		
		//Draw agents max age from a normal distribution (mean = 9125 days (25 years), SD = 1825 days (+- 5 years))
		double lifespan = state.random.nextGaussian() * 1825.0 + 9125.0;
		this.maxAge = Math.max(1, (int)Math.round(lifespan));
		
		//initialize female-specific attributes
		if(!male)
		{
			cycleDay = state.random.nextInt(33) + 1; //females are initialized at a random starting point in their cycle
			gestationRemaining = 0; //not pregnant initially
			matingHistory = new HashMap<>(); //Initialize an empty mating history
		}
		
	}
	
	//utility method for initializing coalition genotype frequency in the initial population of males
	public void initializeGenotype(double initialCoalitionFrequency, MersenneTwisterFast random)
	{
		if(male)
		{
			hasCoalitionGene = random.nextDouble() < initialCoalitionFrequency; //the frequency of males that get the coalition genotype on startup = initialCoalitionFreq
		}
		else
		{
			hasCoalitionGene = false; //females do not have coalition gene
		}
	}
	
	//Method for handling agent death, remove the baboon from the schedule and its group
	public void die(Environment state)
	{
		alive = false;
		event.stop();
		
		if(isMale() && !isJuvenile)
		{
			state.deadMales.add(this); //track dead adult males for probe
			if(state.experimenter != null)
			state.recordDeadMaleStats(this);
		}
		
		if(group != null && group.members != null)
		{
		group.members.remove(this);
		if(isMale() && !isJuvenile)
			{
				group.updateDominanceHierarchyArray();
			}
		}
		else
		{
			System.err.println("WARNING: Baboon died without being assigned to a group."); //added this due to null pointer exception on 5/2/25 debugging
		}
	}
	
	
	//getters and setters for age, sex, and group
	public int getAge() {
		return age;
	}

	public void setAge(int age) {
		this.age = age;
	}


	public boolean isMale() {
		return male;
	}

	public void setMale(boolean male) {
		this.male = male;
	}


	public Group getGroup() {
		return group;
	}

	public void setGroup(Group group) {
		this.group = group;
	}

	public static void resetRunState()
	{
		nextID = 0;
		infantDied = 0;
		infantSurvived = 0;
	}
	
	public int getNumberOfCoalitions()
	{
		return lifetimeCoalitionTracker;
	}
	
	// --- Reproduction Methods for Female Baboons ---

	
	//Updates the reproductive cycle of female agents each time step
	//First updates the cycle day, then records mating events in the fertile window, then determines if the pregnancy occurs at the end of the fertile period.
	//Manages gestation.
	
	public void cycleUpdate()
	{
		// Ensures method only applies to females
		if(male || isJuvenile) return;
		
		// If currently pregnant, decrement gestation
		if(gestationRemaining > 0)
		{
			gestationRemaining--;
			if(gestationRemaining == 0) //When gestation is complete, give birth
			{
				giveBirth();
				cycleDay = 1;
				matingHistory.clear();
			}
			return;
			
		}
		
		//if agent is not male and not pregnant, update the cycle day
		cycleDay++;
		
		//when cycle day exceeds 33, run reproduction method
		if(cycleDay > 33)
		{
			reproduce();
			cycleDay = 1; //Reset the cycle regardless of outcome
			matingHistory.clear();
			currentConsortMale = null; //Clear consortship after fertile period ends
			
		}
		
	}
	
	//Determines if the female becomes pregnant and selects the father of offspring (paternity determination)
	public void reproduce()
	{
		//ensures the method is for females only
		if(male || isJuvenile) return; 
		
		//If no mating events have been recorded, no pregnancy can occur
		//extremely unlikely edge case but should be accounted for so it does not break the simulation
		if(matingHistory == null || matingHistory.isEmpty())
		{
			return;
		}
		
		//If the agent is female and does have a history of copulations with a male in the previous cycle
		//Determine baseline probability of becoming pregnant at the end of the cycle
		double pregnancyProbability = 0.3; // //adjusted on 5/22/2025 from 0.5 to 0.3 in order to control for wild juvenile-adult oscillations from cohort cycling
		if(state.random.nextDouble() < pregnancyProbability)
		{
			//pregnancy occurs
			int totalMatingCount = 0; //counter for calculating total number of recorded reproductive events
			for(Integer count : matingHistory.values())
			{
				totalMatingCount += count;
			}
			
			if(totalMatingCount == 0) //guard against divide by zero error
			{
				return;
			}
			
			//Use weighted random selection algorithm to determine which male will sire offspring based on number of copulations
			//currently, male that sires offspring is selected based on mating frequency
			// *** consider weighting paternity determination by frequency of copulations AS WELL AS timeframe when copulations occured
			int randomChoice = state.random.nextInt(totalMatingCount) + 1; //choose a random number between 1 : totalMateCount inclusive
			int cumulative = 0; 
			Baboon father = null;
			for(Baboon male : matingHistory.keySet()) //for each male in the hashMap
			{
				cumulative += matingHistory.get(male); //add the number of times they mated to the cumulative counter one by one
				if(randomChoice <= cumulative) //if the value of the random number selected is less than or equal to cumulative at that stage of the for loop
				{
					father = male; //that male becomes the father
					father.offspringCount++; // add to father's offspring count tracker
					switch(father.getLifeStage())
					{
					case PRIME -> father.primeOffspring++;
					case POST_PRIME -> father.postPrimeOffspring++;
					case SENESCENT -> father.senescentOffspring++;
					}
					break;
				}
			}
			
			//Begin the gestation period (this encapsulates 165 days of gestation + 185 days of nursing + 60 days post weaning before mother begins cycling, averages estimated from Smuts and Nicolson, 1989)
			gestationRemaining = 410;
			
			this.fatherHasCoalitionGene = father.hasCoalitionGene; //true if the father had the coalition gene, false if not
			
		}
		else
		{
			return; // if agent does not become pregnant, exit the reproduce method
		}
	}
	
	
	//Handles agent birthing event, creates a new baboon agent
	public void giveBirth()
	{
		
		//check if population is at maximum capacity
		int currentPopulation = 0;
		
		for(Object obj : state.sparseSpace.getAllObjects())
		{
			if(obj instanceof Group group)
			{
				currentPopulation += group.members.numObjs;
			}
		}
		
		if(currentPopulation >= state.maxPopulation)
		{
			//System.out.println("Max population reached, Newborn baboon not added to simulation.");
			return;
		}
		
		if(group.members.numObjs >= state.maxGroupSize)
		{
			//System.out.println("Group full: Newborn baboon not added.")
			return;
		}
		
		//assigning a heavily female skewed birth rate caused unstable population dynamics
		//Tried 50/50 female to male birth rate, and used male biased dispersal mortality to try and fix adult/juvenile oscillations as well as sex-ratio stability
		double femaleProbability = 0.50;
		boolean newbornIsMale = (state.random.nextDouble() >= femaleProbability);
		
		//Initialize newborn as a juvenile with age = 245 days (185 days of nursing + 60 days weaned but mother has not started cycling again of the 410 days in gestationRemaining)
		int initialAgeDays = 245;
		boolean isJuvenile = true;
		
		Baboon newborn = new Baboon(state, newbornIsMale, group.x, group.y, initialAgeDays, isJuvenile); // Newborns are created and added to simulation at weaning (185 days post birth)
		newborn.mother = this; //set this female agent as the newborn's mother
		newborn.matrilineID = this.matrilineID; //track the matriline of each baboon for group.fission()
		
		if(newbornIsMale) //decide if male will have coalition gene or not
		{
			if(state.random.nextDouble() < state.mutationRate) //if a mutation occurs
			{
				newborn.hasCoalitionGene = !fatherHasCoalitionGene; //newborn has the coalition gene only if their father DIDNT have it
			}
			else //if mutation does not occur
			{
				newborn.hasCoalitionGene = fatherHasCoalitionGene; //newborn has the coalition gene if their father also has it
			}
			
			//first, set newborn to its mothers group temporarily (because I removed it from its current group in maleImmigration method already)
			newborn.setGroup(this.group);
			this.group.members.add(newborn);
		}
		else
		{
			//if the newborn is female
			newborn.hasCoalitionGene = false; //newborn does not have the coalition gene
			newborn.setGroup(this.group); //assign group to female offspring
			//for newborn females, add them to the current group
			group.members.add(newborn);
		}
		
		// Schedule the newborn for stepping in the simulation
		double now = state.schedule.getTime();
		newborn.event = state.schedule.scheduleRepeating(now + state.scheduleTimeInterval, 0, newborn, state.scheduleTimeInterval);
		
		//clear father genotype information after birth
		fatherHasCoalitionGene = false;
	}
	
	public void recordMating(Baboon male)
	{
		Integer count = matingHistory.get(male); //look up male's value in the hashmap
		if(count == null) //if he doesn't exist in the hashmap
		{
			matingHistory.put(male, 1); //add him to it and put his value at 1
		}
		else //if he already exists in the hashmap
		{
			matingHistory.put(male, count + 1); //update his value in the hashmap so it increases by 1
		}
	}
	
	//Method for Juvenile to mature
	public void mature()
	{
		//Check and make sure agent is a juvenile before continuing
		if(!isJuvenile) return;
		
		//Change juvenile tag to false to indicate agent is mature
		isJuvenile = false;
		
		//if agent is male, handle fighting ability update and migration event
		if(male)
		{
			if(state.random.nextDouble() < (state).migrationMortalityRate) //to control cohort cyclying effects, remove a portion of males when they disperse (captures predation and should stabilize population dynamics)
			{
				this.die(state);
				return;
			}
			else
			{
			maleImmigrationNew();
			}
		}
		else //for female agents, initialize their estrous cycle
		{
			this.cycleDay = state.random.nextInt(33) + 1;
			this.gestationRemaining = 0;
			this.matingHistory = new HashMap<>();
		}
	}
	
	// culls 25% of juvenile agents at 1 year old to reflect infant mortality rate reported in Alberts(2017)
	public void checkInfantSurvival()
	{
		
		//Ensure this method only applies to juveniles
		if(!isJuvenile) return;
		
		//Check if juvenile has reached 365 days of age
		if(age == 365)
		{
			if(state.random.nextDouble() < 0.25)
			{
				infantDied++;
				die(state);
			}
			else
			{
				infantSurvived++;
			}
		}
	}
	
	
	/// --- Methods for Males ---
	
	public LifeStage getLifeStage()
	{
		if(isJuvenile || age < 2555) return LifeStage.JUVENILE; // if age is between 0-2554 days (0-7 years old), male is juvenile
		if(age < 5475) return LifeStage.PRIME; //if age is between 7-15 years old, consider prime male
		if(age < 7300) return LifeStage.POST_PRIME; //if age is between 15-20 years old, post-prime male with potential coalition formation ability
		return LifeStage.SENESCENT; //few agents that live past 20 can form coalitions but will be very weak comparativley
	}
	
	
	//dispersal method for adult males
	/*
	 * Old male dispersal method, rewrote it in maleImmigrationNew() to stop clustering of agents that immigrate too locally
	 * 
	public void maleImmigration()
	{
		Group oldGroup = this.group;
		//Use findGroupNearest method to find a new group
		Group newGroup = state.findGroupNearest(x, y, state.sparseSpace.TOROIDAL);
		
		//If a new group is found, proceed with migration
		if(newGroup != null)
		{
			//remove the baboon from its current group
			group.members.remove(this);
			
			//Update the baboon's spatial coordinate to match the new group's location
			this.x = newGroup.x;
			this.y = newGroup.y;
			
			//Set the baboon's group reference to the new group
			setGroup(newGroup);
			
			//Add the baboon to the new group's member list
			newGroup.members.add(this);
			
			oldGroup.updateDominanceHierarchyArray();
			newGroup.updateDominanceHierarchyArray();
		}
	}
	*/
	
	public void maleImmigrationNew()
	{
		Group natalGroup = this.group; //assign a pointer to the group the male is migrating out of at maturity
		
		//Draw initial search radius randomly between 1-25
		int initialRadius = state.random.nextInt(25) + 1;
		
		Group newGroup = null;
		int maxDim = Math.max(state.sparseSpace.getWidth(), state.sparseSpace.getHeight());
		
		for (int r = initialRadius; r <= maxDim; r++)
		{
			Bag candidates = state.findGroupWithinMooreRadius(x,y,r);
			if(candidates != null && candidates.numObjs > 0)
			{
				newGroup = (Group)candidates.get(state.random.nextInt(candidates.numObjs));
				break;
			}
		}
		
		if(newGroup == null) return;
		
		//move to new group
		if(group != null)
		{
			group.members.remove(this);
		}
		this.x = newGroup.x;
		this.y = newGroup.y;
		setGroup(newGroup);
		newGroup.members.add(this);
		
		//update dominance hierarchies
		if(natalGroup != null)
		{
			natalGroup.updateDominanceHierarchyArray();
		}
		newGroup.updateDominanceHierarchyArray();
	}
	
	public double calculateFightingAbility()
	{
		/*
	    - Upon migrating, quickly rise in rank (to top or top 2 spots)
	    - fighting ability starts very high, drops over time as male ages
	    - As new young males join the group, males drop in dominance ranking
	    */
		
		//Fighting ability is peak around 10 years old (3650 time steps), then declines by maxAge
		double maxAbility = 1.0;
		int peakAge = 3650; //10 years in days (timesteps)
		double declineRatePerDay = 0.05 / 365.0; //5% decline rate per year
		
		//All agents are born mature, so we can just apply the decline rate from peak age
		int daysSinceMaturity = age - peakAge;
		double ability = maxAbility - (daysSinceMaturity * declineRatePerDay);
		
		return Math.max(ability, 0.0); //fighting ability should have limits between 0-1, return whichever value is larger
	}
	
	//use a logistic curve instead of linear decrease in fighting ability
	public double calculateFightingAbilityLogistic()
	{
		int x = age;
		double k = 0.0015; //steepness of logistic decay
		int x0 = 5475; //midpoint
		
		double ability = 1.0 / (1.0 + Math.exp(k * (x - x0)));
		return ability;
		
	}
	
    public String baboonIdToString()
    {
    	return "Baboon[ID=" + ID + ", sex=" + (isMale() ? "M" : "F") + ", age=" + age + "]";
    }


	@Override
	public void step(SimState state)
	{
		age++; //increase age by 1 timestep (1 day)
		
		if(isJuvenile && age == 365)
		{
			checkInfantSurvival();
			if(!alive) return;
		}
		if(isJuvenile)
		{
			if((isMale() && age >= 2555) || (!isMale() && age >= 2190)) //if juvenile is male, matures at 7 y/o, if juvenile is female matures at 6
			{
			mature();
			}
		}
		
		//moran-like process breaks the population dynamic
		/*if(state.schedule.getSteps() >= 50 && state.schedule.getSteps() % 50 == 0) //implement density dependent mortality starting at step 50 and every 50 steps afterward
		{
			checkDensityDependentMortality();
		}*/
		
		if(age >= maxAge) // when agent ages past their individual lifespan, they die
		{
			die(this.state);
			return;
		}
		
		//update female reproduction
		if(!male && !isJuvenile)
		{
			cycleUpdate();
		}
		
		
	}

}