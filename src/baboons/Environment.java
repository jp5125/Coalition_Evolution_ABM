package baboons;
import sim.engine.SimState;
import spaces.Spaces;
import sweep.SimStateSweep;
import sim.util.Bag;
import sim.engine.*;
import java.util.*;
import java.util.ArrayList;

public class Environment extends SimStateSweep implements Steppable
{
	public enum SuccessMode {
		COIN_FLIP,
		FA_LOGISTIC,
		FA_LOGISTIC_HERDING
	}

	public enum ParticipationMode {
		BASELINE,
		STATE_THRESHOLD,
		STATE_EV
	}

	public enum AlternativeMode {
		NONE,
		SNEAKER
	}
	
	

	//population variables
	public int n =10000; //number of baboons at simulation start
	public int groups = 175; //number of groups at simulation start
	public int minGroups = 20;
	public int minGroupSize = 12;
	public int maxGroupSize = 125; //***adjust group sizes to capture that only adults are in population, no juveniles. thus total group size is smaller than in wild***
	public int maxPopulation = 25000; 
	
	//reproduction variables
	public double initialCoalitionFrequency = 0.01; //frequency of coalition gene in adult males at simulation start
	public double mutationRate = 0.01; //rate of mutations in cooperative genotype
	public double migrationMortalityRate = 0.30; //30% chance a male will die when leaving their natal group

	//coalition mode switches
	public SuccessMode successMode = SuccessMode.FA_LOGISTIC;
	public ParticipationMode participationMode = ParticipationMode.BASELINE;
	public AlternativeMode alternativeMode = AlternativeMode.NONE;

	//coalition parameters
	public double probMortalWoundConsort = 0.00;
	public double probMortalWoundCoalition = 0.01;
	public double cost = 0.01; //fighting ability cost for participating in a fight
	public double coalitionWinBeta = 1.0; 
	public double coalitionDefenseBonus = 1.0;
	public double coalitionFaThreshold = 0.5;
	public int coalitionRankThreshold = 5;
	public boolean requireCoalitionGeneForStateDependentMode = true;
	public static double coalitionBenefit = 1.0;
	public double coalitionChallengeCost = 0.0;
	public double baselineMatingBenefit = 0.0;
	public double futureFitnessWeight = 1.0;
	public double sneakerSuccessProbability = 0.1;
	public boolean disruptionEnablesSneaking = true;
	
	//coalition measurments
	public double avgCoalitionsPerMale = 0.0;
	public double avgCoalitionParticipationCost = 0.0;
	
	//age variables
	public double averageAge; 
	
	//references for data collection
	public static Bag deadMales = new Bag();
	public int malesWithGene = 0;
	public int malesWithoutGene = 0;
	public Experimenter experimenter;
	
	//population summary state variables

	public int totalBaboons = 0;
	public int juvenileCount = 0;
	public int adultMaleCount = 0;
	public int adultFemaleCount = 0;
	public int coalitionGeneCount = 0;
	
	//derived summary state variables
	public int totalAdults = 0;
	public double avgFightingAbility = 0.0;
	public double coalitionGeneFreq = 0.0;
	public double avgDominanceRank = 0.0;
	public double avgDominanceHierarchySize = 0.0;
	
	//other summary state variables
	public int maxDominanceRank = 0;
	public int maxGroupSizeObserved = 0;
	public long summaryStep = 0;
	
	
	//getters and setters
	
	public int getN() {
		return n;
	}

	public void setN(int n) {
		this.n = n;
	}

	public double getMutationRate() {
		return mutationRate;
	}

	public void setMutationRate(double mutationRate) {
		this.mutationRate = mutationRate;
	}

	public double getInitialCoalitionFrequency() {
		return initialCoalitionFrequency;
	}

	public void setInitialCoalitionFrequency(double initialCoalitionFrequency) {
		this.initialCoalitionFrequency = initialCoalitionFrequency;
	}

	public double getAverageAge() {
		return averageAge;
	}

	public void setAverageAge(double averageAge) {
		this.averageAge = averageAge;
	}
	
	public int getGroups() {
		return groups;
	}

	public void setGroups(int groups) {
		this.groups = groups;
	}
	
	public int getMaxPopulation(){
		return maxPopulation;
	}
	
	public void setMaxPopulation(int maxPopulation) {
		this.maxPopulation = maxPopulation;
	}
	
	public double getMigrationMortalityRate()
	{
		return migrationMortalityRate;
	}
	
	public void setMigrationMortalityRate(double rate)
	{
		this.migrationMortalityRate = rate;
	}
	
	public double getProbMortalWoundCoalition() {
		return probMortalWoundCoalition;
	}

	public void setProbMortalWoundCoalition(double probMortalWoundCoalition) {
		this.probMortalWoundCoalition = probMortalWoundCoalition;
	}
	
	public double getCost() {
		return cost;
	}

	public void setCost(double cost) {
		this.cost = cost;
	}
	
	//methods for environment
	public Environment(long seed, Class observer)
	{
		super(seed, observer);
		
	}
	
	
	// Method to make groups upon simulation initialization
	public void makeGroups()
	{
		//allow for variable group sizes upon initialization
		int totalAgentsAssigned = 0;
		int maxJuveniles = (int) (n * 0.2); //ensure that the simulation cannot be comprised of more than 20% juveniles upon initialization
		int totalJuvenilesAssigned = 0;
		
		for(int i = 0; i < groups; i++) //for each group
		{
			int remaining = n - totalAgentsAssigned;
			if(remaining <= 0)
			{
				break;
			}

			int x = random.nextInt(gridWidth); //give group random x coordinate
			int y = random.nextInt(gridHeight); //give group random y coordinate
			int maxAllowedGroupSize = Math.min(maxGroupSize, remaining);
			int minAllowedGroupSize = Math.min(minGroupSize, maxAllowedGroupSize);
			int groupSize;
			if(minAllowedGroupSize == maxAllowedGroupSize)
			{
				groupSize = maxAllowedGroupSize;
			}
			else
			{
				groupSize = random.nextInt(maxAllowedGroupSize - minAllowedGroupSize + 1) + minAllowedGroupSize; //generates groups between minimum and maximum group size
			}
			Bag g = new Bag(groupSize); //create bag called g with capacity equal to groupSize
			
			
			ArrayList<Baboon> adultFemales = new ArrayList<>();
			
			for(int j = 0; j < groupSize; j++) //add agents to groups
			{
				boolean isMale = (random.nextDouble() >= 0.714); //2.5:1 OSR
				int ageInDays;
				boolean isJuvenile = false;
				
				//decide if agent is going to be a juvenile or not based on if there are too many juveniles and a probability calculation
				if(totalJuvenilesAssigned < maxJuveniles && random.nextDouble() < 0.2)
				{
					isJuvenile = true; //set the agent's juvenile tag to true
					ageInDays = random.nextInt(3650 - 185) + 185; //initialize at an age between weaning (185 days after birth) and sexual maturity (10 y/o)
					totalJuvenilesAssigned++; //increase number of juveniles assigned
				}
				else //if the agent will be initialized as an adult
				{
					int ageInYears = random.nextInt(16) + 10; //ages 10-25 in years
					ageInDays = ageInYears * 365; //translate age in years upon initialization to age in days (timesteps)
				}
				
				Baboon b = new Baboon(this, isMale, x, y, ageInDays, isJuvenile); //create a new baboon for each baboon in the group
				if(b.isMale())
				{
					b.initializeGenotype(initialCoalitionFrequency, random); //initialize coalition gene in starting males
					
					if(isJuvenile && !adultFemales.isEmpty())
					{
						Baboon mom = adultFemales.get(random.nextInt(adultFemales.size()));
						b.matrilineID = mom.matrilineID;
					}
					if(isJuvenile && adultFemales.isEmpty())
					{
						b.matrilineID = null; //set juvenile male matrilineID null for now
					}
					//note, adult males at genesis do not need a matrilineID, as mID is only important for juvenile males during fission
				}
				if(!b.isMale()) //if b is not a male
				{
					if(!isJuvenile) // if b is not male and is not juvenile
					{
					b.matrilineID = "mat_" + b.ID; //create matrilines based on starting females 
					adultFemales.add(b); //add this individual to the group's bag of adultFemales
					}
					else
					{
						if(!adultFemales.isEmpty()) //if there are adult females that were created before a juvenile was
						{
							Baboon mom = adultFemales.get(random.nextInt(adultFemales.size())); //randomly select one as the mother
							b.matrilineID = mom.matrilineID; //assign mother's matrilineID as new juvenile's matrilineID
							b.mother = mom; //give 'mom' the mother pointer
						}
						else
						{
							b.matrilineID = null; //if juveniles are created before any adults, we will set their matrilineID to null until an adult is created 
						}
					}
				}
				g.add(b);
			}
			
			//second pass for assigning matrilines to juveniles that were created before any adults
			ArrayList<Baboon> adultFemales2 = new ArrayList<>();
			for(int k = 0; k < g.numObjs; k++)
			{
				Baboon mom = (Baboon) g.objs[k];
				if(!mom.isMale() && !mom.isJuvenile)
				{
					adultFemales2.add(mom);
				}
			}
			
			if(!adultFemales2.isEmpty())
			{
				for(int k = 0; k < g.numObjs; k++)
				{
					Baboon juv = (Baboon) g.objs[k];
					if(juv.matrilineID == null && juv.isJuvenile)
					{
						Baboon mom = adultFemales2.get(random.nextInt(adultFemales2.size()));
						juv.matrilineID = mom.matrilineID;
						juv.mother = mom;
					}
				}
			}
			else //to account for rare cases where no adult females were put into a group at genesis (extremely rare but a statistical possibility)
			{ 
				
				for(int k = 0; k < g.numObjs; k++)
				{
					Baboon b = (Baboon) g.objs[k];
					if(b.matrilineID == null)
					{
						b.matrilineID = "Orph_mat___" + b.ID;
					}
				}
			}
			
			totalAgentsAssigned += groupSize; //add the number of agents in the newly formed group to our count of totalAgentsAssigned
			
			Group group = new Group(this, x,y,g);
			group.event = schedule.scheduleRepeating(1.0, 1, group, scheduleTimeInterval);
			sparseSpace.setObjectLocation(group, x, y);
			
			for(int k = 0; k < g.numObjs; k++) //Assigns group reference to each baboon
			{
				Baboon b = (Baboon) g.objs[k];
				b.setGroup(group);
				b.event = schedule.scheduleRepeating(1,0,b);
			}
			
			System.out.printf("Group %d initialized at (%d, %d) with %d members.\n", i + 1, x, y, groupSize); //log each groups starting size at startup
			
			if(totalAgentsAssigned >= n) break; //if all agents are assigned to groups, exit loop
		}
		
	}
	
	//utility method for finding nearest group in simulation. Used by Group.groupDisperse() and Baboon.maleImmigration()
	public Group findGroupNearest(int x, int y, int mode)
	{
		if(sparseSpace.getAllObjects().numObjs < 2)
			return null;
		
		Bag groups; //create an empty bag of other groups in the simulation
		int radius = 1; //starting search radius
		Group nearestGroup = null; // stores the nearest group upon completion of the method
		
		while(nearestGroup == null) //loop until a nearest group is found
		{
			groups = sparseSpace.getMooreNeighbors(x, y, radius, mode, false); //all groups that are within the moores neighborhood are added to the bag of neighbors
			groups.shuffle(random); //randomly shuffles the order of the bag
			
			for(Object obj : groups) //this logic draws the first group from the bag of neighbors, sets it to nearestGroup, and breaks
			{
				Group g = (Group) obj; 
				if(g.members != null && g.members.numObjs > 0 && g.members.numObjs < maxGroupSize)
				{
					nearestGroup = g;
					break;
				}
			}
			radius++; //increase the radius if no neighbors in the previous radius' moores neighborhood
			if(radius > Math.max(gridWidth, gridHeight)) //stops search from expanding past the size of the environment
			{
				break;
			}
		}
		return nearestGroup;
	}
	
	//utility method for returning bag of potential groups within moore's radius (moores neighborhood that expands out to the radius calculated for each migrating male agent) of focal group
	public Bag findGroupWithinMooreRadius(int x, int y, int r)
	{
		Bag neighbors = sparseSpace.getMooreNeighbors(x,  y,  r,  sparseSpace.TOROIDAL, false);
		
		Bag groups = new Bag();
		HashSet<Group> seen = new HashSet<>();
		
		if(neighbors != null)
		{
			for(int i = 0; i < neighbors.numObjs; i++)
			{
				Object o = neighbors.objs[i];
				if(o instanceof Group)
				{
					Group g = (Group) o;
					if(seen.add(g))
					{
						if(g.members != null && g.members.numObjs > 0 && g.members.numObjs < maxGroupSize)
						{
							groups.add(g);
						}
					}
				}
			}
		}
		
		return groups;
		
	}
	
	//Utility method to get the current number of groups
	public int getTotalGroups()
	{
		//returns 0 when MASON inspects getTotalGroupsbefore initialization,  prevents nullPointerError
		if(sparseSpace == null)
		{
			return 0;
		}
		
		int groupCount = 0;
		for(Object obj : sparseSpace.getAllObjects())
		{
			if(obj instanceof Group)
			{
				groupCount++;
			}
		}
		return groupCount;
	}
	
	//utility method for calculating the average number of coalitions an individual male 
	public double calculateAverageNumberOfCoalitions() 
	{
	    int sum = 0;
	    int maleCount = 0;

	    for (Object obj : sparseSpace.getAllObjects()) //for each group in the simulation
	    {
	        if (obj instanceof Group) 
	        {
	            Group g = (Group) obj; //assign said group to 'g'

	            for (int i = 0; i < g.members.numObjs; i++) //for each agent in g
	            {
	                Baboon b = (Baboon) g.members.objs[i]; //cast each agent as a baboon object

	                if (b.isMale() && !b.isJuvenile && b.hasCoalitionGene) //if that baboon object is male, an adult, and has the coalition gene
	                {
	                    sum += b.lifetimeCoalitionTracker; //extract the value of their lifetime coalition tracker attribute and add it to sum
	                    maleCount++;
	                }
	            }
	        }
	    }

	    if (maleCount == 0) //if there are no coalition males in the group
	    {
	        return 0.0; 
	    }

	    return (double) sum / maleCount; //otherwise, use the value of sum divided by male count to calculate the average number of coalitions males participate in
	}
	
	public double calculateVarianceCorrectedCoalitionParticipationCost()
	{
		double sumOfCosts = 0.0;
		int maleCount = 0;
		double survivalLikelihood = (1 - probMortalWoundCoalition);
		
		
		
		for(Object obj : sparseSpace.getAllObjects()) //for each group in the simulation
		{
			if(obj instanceof Group)
			{
				Group g = (Group) obj;
				
				for(int i = 0; i < g.members.numObjs; i++) //for each agent within each group
				{
					Baboon b = (Baboon) g.members.objs[i];
					
					if(b.isMale() && !b.isJuvenile && b.hasCoalitionGene) //if the agent is an adult male with the coalition gene
					{
						double numCoalitions = b.getNumberOfCoalitions();
						double result = Math.pow(survivalLikelihood, numCoalitions);
						double expectedCost = 1 - result;
						
						sumOfCosts += expectedCost;
						maleCount++;
						
					}
				}
			}
		}
		
		if(maleCount == 0.0)
		{
			return 0.0;
		}
		
		return sumOfCosts / maleCount;
	}
	
	public void updateDebugSummaryStats()
	{
		totalBaboons = 0;
	    juvenileCount = 0;
	    adultMaleCount = 0;
	    adultFemaleCount = 0;
	    coalitionGeneCount = 0;

	    double totalFightingAbility = 0.0;
	    maxDominanceRank = 0;
	    maxGroupSizeObserved = 0;
	    int totalDominanceRank = 0;
	    int totalDominanceHierarchySize = 0;
	    int groupCount = 0;
	    
	    for (Object obj : sparseSpace.getAllObjects())
	    {
	        if (obj instanceof Group group)
	        {
	            groupCount++;

	            int groupAdultMales = 0;
	            for (int i = 0; i < group.members.numObjs; i++)
	            {
	                Baboon b = (Baboon) group.members.objs[i];
	                if (b.isMale() && !b.isJuvenile)
	                {
	                    groupAdultMales++;
	                }
	            }
	            totalDominanceHierarchySize += groupAdultMales;

	            if (group.members.numObjs > maxGroupSizeObserved)
	            {
	                maxGroupSizeObserved = group.members.numObjs;
	            }

	            HashSet<Integer> seenRanks = new HashSet<>();

	            for (int i = 0; i < group.members.numObjs; i++)
	            {
	                Baboon b = (Baboon) group.members.objs[i];
	                totalBaboons++;

	                if (b.isJuvenile)
	                {
	                    juvenileCount++;
	                }

	                if (b.isMale() && !b.isJuvenile)
	                {
	                    adultMaleCount++;
	                    totalFightingAbility += b.fightingAbility;
	                    totalDominanceRank += b.dominanceRank;

	                    if (b.hasCoalitionGene)
	                    {
	                        coalitionGeneCount++;
	                    }

	                    if (b.dominanceRank > group.members.numObjs)
	                    {
	                        System.out.println("Baboon ID " + b.ID + " has INVALID rank " + b.dominanceRank +
	                                " (group size: " + group.members.numObjs + ")");
	                    }

	                    if (!seenRanks.add(b.dominanceRank))
	                    {
	                        System.out.println("Duplicate rank " + b.dominanceRank + " found (Baboon ID " + b.ID + ")");
	                    }

	                    if (b.dominanceRank > maxDominanceRank)
	                    {
	                        maxDominanceRank = b.dominanceRank;
	                    }
	                }

	                if (!b.isMale() && !b.isJuvenile)
	                {
	                    adultFemaleCount++;
	                }
	            }
	        }
	    }

	    totalAdults = adultMaleCount + adultFemaleCount;
	    avgFightingAbility = adultMaleCount > 0 ? totalFightingAbility / adultMaleCount : 0.0;
	    coalitionGeneFreq = adultMaleCount > 0 ? (double) coalitionGeneCount / adultMaleCount : 0.0;
	    avgDominanceRank = adultMaleCount > 0 ? (double) totalDominanceRank / adultMaleCount : 0.0;
	    avgDominanceHierarchySize = groupCount > 0 ? (double) totalDominanceHierarchySize / groupCount : 0.0;
	    avgCoalitionsPerMale = calculateAverageNumberOfCoalitions();
	    avgCoalitionParticipationCost = calculateVarianceCorrectedCoalitionParticipationCost();
	    summaryStep = schedule.getSteps();
	}
	
	//utility method for printing model outputs to the console for the purpose of debugging BEFORE data collection
	public void printDebugSummary()
	{
		
		
		updateDebugSummaryStats();
		
		
		System.out.printf(
				"[Step %d] Total: %d | Adults: %d | Juveniles: %d | Males: %d | Females: %d | Coalition Gene: %d (%.2f%%) | Avg FA: %.3f\n",
		        summaryStep, totalBaboons, totalAdults, juvenileCount, adultMaleCount, adultFemaleCount,
		        coalitionGeneCount, coalitionGeneFreq * 100, avgFightingAbility
		    );
		
		System.out.printf("Max Dominance Rank Observed: %d\n", maxDominanceRank);
		System.out.printf("Max Group Size Observed: %d\n", maxGroupSizeObserved);
		System.out.printf("Average Dominance Rank (Adult Males): %.2f\n", avgDominanceRank);
		System.out.printf("Average Dominance Hierarchy Size (Adult Males per Group): %.2f\n", avgDominanceHierarchySize);
		System.out.printf("Average number of coalitions males participate in: %.2f%n", avgCoalitionsPerMale);
		System.out.printf("Average mortality risk males take by joining coalitions throughout their life, account for individual variation: %.2f%n", 
				avgCoalitionParticipationCost);
		System.out.println(" ");
	}
	
	public void start()
	{
		super.start();
		deadMales.clear();
		Baboon.resetRunState();
		spaces = Spaces.SPARSE; //set the space
		make2DSpace(spaces, gridWidth, gridHeight);//make the space
		makeGroups(); //create the groups
		
		schedule.scheduleRepeating(this);
		if(observer != null)
		{
			observer.initialize(sparseSpace, spaces); // initialize the experimenter by calling initialize in the parent class
			experimenter = (Experimenter) observer;
			experimenter.resetVariables();
			
		}
		
	}
	
	
	public void step(SimState state)
	{
	
		if(schedule.getSteps() % 100 == 0)
		{
			updateDebugSummaryStats();
			printDebugSummary();
		}
		
	}

}