package baboons;

import observer.Observer;
import sim.engine.SimState;
import sim.util.Bag;
import sweep.CustomData;
import sweep.ParameterSweeper;
import sweep.SimStateSweep;
import java.util.*;
import sim.util.media.*;
import sim.util.media.chart.BarChartGenerator;
import sim.util.gui.*;

public class Experimenter extends Observer
{
	
	//fields for tracking Coalition gene Carriers (CC)
		public double ccPrime = 10;
		public double ccPostPrime = 10;
		public double ccSenescent = 10;
		public double ccN = 30; //number of dead coalition gene males
		
		//fields for tracking Non-Carriers of coalition gene (NG)
		public double ncPrime = 5;
		public double ncPostPrime = 5;
		public double ncSenescent = 5;
		public double ncN = 15; //number of dead non-coalition gene males
		
		public BarChartGenerator reproductiveBarChart; 
		

	public Experimenter(String fileName, String folderName, SimStateSweep state, ParameterSweeper sweeper,
			String precision, String[] headers) 
	{
		super(fileName, folderName, state, sweeper, precision, headers);
	}
	
	public void resetVariables()
	{
		ccPrime = 0;
		ccPostPrime = 0;
		ccSenescent = 0;
		ccN = 0; 
		
		ncPrime = 0;
		ncPostPrime = 0;
		ncSenescent = 0;
		ncN = 0;
		
	}
	
	public void numberOfCoalitionMales(Environment state, double time) 
	{
	    Bag groups = state.sparseSpace.getAllObjects();
	    int malesWithGene = 0;
	    int totalMales = 0;

	    for (int g = 0; g < groups.numObjs; g++) 
	    {
	        Group group = (Group) groups.objs[g];
	        Bag members = group.members;

	        for (int i = 0; i < members.numObjs; i++) 
	        {
	            Baboon baboon = (Baboon) members.objs[i];
	            if (baboon.isMale() && !baboon.isJuvenile) 
	            {
	                totalMales++;
	                if (baboon.hasCoalitionGene) 
	                {
	                    malesWithGene++;
	                } 
	            }
	        }
	    }
	    
	    int malesWithoutGene = totalMales - malesWithGene;

	    long interval = 1000;

	    // Update count chart (Chart 0)
	    //plots number of males with coalition gene
	    this.upDateTimeChart(0, time, malesWithGene, true, interval);
	    

	    // Update percentage chart (Chart 1)
	    if (totalMales > 0) 
	    {
	    	//percentage of males with the coalition gene
	        double pctWithGene = (malesWithGene * 100.0) / totalMales;
	        double pctWithoutGene = 100.0 - pctWithGene;
	        
	        //plot this percentage
	        this.upDateTimeChart(1, time, pctWithGene, true, interval);
	        
	    }
	}
	
	public void maleOffspringByStrategy(Baboon male)
	{
		if(!male.isMale() || male.isJuvenile) return;
		
		if(male.hasCoalitionGene)
		{
			ccPrime += male.primeOffspring;
			ccPostPrime += male.postPrimeOffspring;
			ccSenescent += male.senescentOffspring;
			ccN++;
		}
		else
		{
			ncPrime += male.primeOffspring;
			ncPostPrime += male.postPrimeOffspring;
			ncSenescent += male.senescentOffspring;
			ncN++;
		}
	}
	
	/*
	public void updateReproductiveBarChart(SimState state)
	{
		
		if(!this.state.gui.chartTypeBar || this.state.gui.chartBar == null)
		{
			System.out.println("Bar Chart is not initialized or null!");
			return;
		}
		
		double[] values = new double[]
		{
			ccPrime,
			ccPostPrime,
			ccSenescent,
			ncPrime,
			ncPostPrime,
			ncSenescent
		};
		
		String[] labels = new String[] {
			    "ccPrime", "ccPostPrime", "ccSenescent",
			    "ncPrime", "ncPostPrime", "ncSenescent"
			};
		
		int time = (int) state.schedule.getSteps();
		long updateInterval = ((SimStateSweep) state).getDataSamplingInterval();
		
		upDatBarChart(time, values, labels, updateInterval);
				
	}
	*/
	
	public boolean nextInterval(Environment state)
	{
		if(!state.paramSweeps)
		{
			return false;
		}
		state.updateDebugSummaryStats();
		
		data.add(state.coalitionGeneCount); //total number of males with the coalition gene, A
		data.add(state.coalitionGeneFreq); //proportion of males with the coalition gene out of all males (coalition gene and no coalition gene), B
		data.add(state.totalBaboons); //total number of baboons in the simulation, C
		data.add(state.juvenileCount); //total number of juvenile baboons in the sim, D
		data.add(state.adultMaleCount); //total number of adult males in the sim, E
		data.add(state.adultFemaleCount); //total number of adult females in the sim, F
		data.add(state.avgFightingAbility); //the average fighting ability value across all populations, G
		data.add(state.avgDominanceRank); //average dominance rank across all populations, H
		data.add(state.avgDominanceHierarchySize); //average size of a dominance hierarchy across all populations, I
		data.add(state.avgCoalitionsPerMale); //average number of coalitions males participate in throughout their lives, J
		data.add(state.avgCoalitionParticipationCost); //the average mortality probability any given male has across their lifetime (from coalition participation), K
		
		data.add(state.meanLifetimeOffspringCoalitionMales); //average lifetime offspring of coalition males, L
		data.add(state.meanLifetimeOffspringNonCoalitionMales); //average lifetime offspring of non coalition males, M
		
		//average offspring being sired by males of each age class w/ the coaliton gene
		data.add(state.avgOffspringPrimeCoalitionMales); //N
		data.add(state.avgOffspringPostPrimeCoalitionMales); //O
		data.add(state.avgOffspringSenescentCoalitionMales); //P
		
		//average offspring being sired by males of each age class w/o the coalition gene
		data.add(state.avgOffspringPrimeNonCoalitionMales); //Q
		data.add(state.avgOffspringPostPrimeNonCoalitionMales); //R
		data.add(state.avgOffspringSenescentNonCoalitionMales); //S

		//average lifespan of coalition and non coalition males
		data.add(state.avgLifespanCoalitionMales); //T
		data.add(state.avgLifespanNonCoalitionMales); //U

		//average dominance rank of coalition and non coalition males
		data.add(state.avgDominanceRankCoalitionMales); //V
		data.add(state.avgDominanceRankNonCoalitionMales); //W
		
		//average fighting ability of coalition and non coalition males
		data.add(state.avgFightingAbilityCoalitionMales); //X
		data.add(state.avgFightingAbilityNonCoalitionMales); //Y
		
		
		return false;
	}
	
	public void step(SimState state)
	{
		super.step(state);
		if(step % this.state.dataSamplingInterval == 0)
		{
			double time = state.schedule.getTime();
			
			numberOfCoalitionMales((Environment) state, time);
			//updateReproductiveBarChart(state);
			 
		}
	}

}
