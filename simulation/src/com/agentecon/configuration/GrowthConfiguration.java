/**
 * Created by Luzius Meisser on Jun 19, 2017
 * Copyright: Meisser Economics AG, Zurich
 * Contact: luzius@meissereconomics.com
 *
 * Feel free to reuse this code under the MIT License
 * https://opensource.org/licenses/MIT
 */
package com.agentecon.configuration;

import java.io.IOException;
import java.io.PrintStream;
import java.net.SocketTimeoutException;
import java.util.Collection;

import com.agentecon.IAgentFactory;
import com.agentecon.ISimulation;
import com.agentecon.agent.Endowment;
import com.agentecon.agent.IAgent;
import com.agentecon.agent.IAgentIdGenerator;
import com.agentecon.consumer.Consumer;
import com.agentecon.consumer.IConsumer;
import com.agentecon.consumer.IUtility;
import com.agentecon.events.GrowthEvent;
import com.agentecon.events.IUtilityFactory;
import com.agentecon.exercises.ExerciseAgentLoader;
import com.agentecon.exercises.FarmingConfiguration;
import com.agentecon.exercises.HermitConfiguration;
import com.agentecon.firm.production.CobbDouglasProductionWithFixedCost;
import com.agentecon.goods.Good;
import com.agentecon.goods.IStock;
import com.agentecon.goods.Inventory;
import com.agentecon.goods.Quantity;
import com.agentecon.goods.Stock;
import com.agentecon.market.GoodStats;
import com.agentecon.market.IStatistics;
import com.agentecon.production.IProductionFunction;
import com.agentecon.production.PriceUnknownException;
import com.agentecon.research.IInnovation;
import com.agentecon.world.ICountry;

public class GrowthConfiguration extends FarmingConfiguration implements IUtilityFactory, IInnovation {
	
	private static final int BASIC_AGENTS = 30;
	public static final String BASIC_AGENT = "com.agentecon.exercise4.Farmer";
	
	public static final double GROWTH_RATE = 0.002;
	public static final int MAX_AGE = 500;

	public static final Good LAND = HermitConfiguration.LAND;
	public static final Good POTATOE = HermitConfiguration.POTATOE;
	public static final Good MAN_HOUR = HermitConfiguration.MAN_HOUR;

	@SafeVarargs
	public GrowthConfiguration(Class<? extends Consumer>... agents) {
		this(new AgentFactoryMultiplex(agents), BASIC_AGENTS);
	}
	
	public GrowthConfiguration() throws SocketTimeoutException, IOException {
		this(new ExerciseAgentLoader(BASIC_AGENT), BASIC_AGENTS);
	}
	
	public GrowthConfiguration(IAgentFactory loader, int agents) {
		super(new IAgentFactory() {
			
			private int number = 1;
			
			@Override
			public IConsumer createConsumer(IAgentIdGenerator id, Endowment endowment, IUtility utilityFunction) {
				int maxAge = number++ * MAX_AGE / agents;
				return loader.createConsumer(id, maxAge, endowment, utilityFunction);
			}
		}, agents);
		IStock[] dailyEndowment = new IStock[] { new Stock(MAN_HOUR, HermitConfiguration.DAILY_ENDOWMENT) };
		Endowment workerEndowment = new Endowment(getMoney(), new IStock[0], dailyEndowment);
		addEvent(new GrowthEvent(0, GROWTH_RATE){

			@Override
			protected void execute(ICountry sim) {
				IConsumer cons = loader.createConsumer(sim, MAX_AGE, workerEndowment, create(0));
				sim.add(cons);
			}
			
		});
//		addEvent(new InterestEvent(0.001, 1));
	}

	public void diagnoseResult(PrintStream out, ISimulation sim) {
		try {
			IStatistics stats = sim.getStatistics();
			CobbDouglasProductionWithFixedCost prodFun = createProductionFunction(POTATOE);
			GoodStats manhours = stats.getGoodsMarketStats().getStats(MAN_HOUR);
			double laborShare = prodFun.getWeight(MAN_HOUR).weight;
			double optimalNumberOfFirms = manhours.getYesterday().getTotWeight() / FarmingConfiguration.FIXED_COSTS.getAmount() * (1 - laborShare);
			int numberOfFirms = sim.getAgents().getFirms().size();
			System.out.println(manhours + " implies optimal number of firms k=" + optimalNumberOfFirms + ", actual number of firms is " + numberOfFirms);
			System.out.println(stats.getGoodsMarketStats());

			Inventory inv = new Inventory(getMoney());
			double totalLand = getTotalLand(sim.getAgents().getAgents());
			double optimalCost = prodFun.getCostOfMaximumProfit(inv, stats.getGoodsMarketStats());
			double optimalManhours = optimalCost / stats.getGoodsMarketStats().getPriceBelief(MAN_HOUR);
			double fixedCosts = prodFun.getFixedCost(MAN_HOUR) * stats.getGoodsMarketStats().getPriceBelief(MAN_HOUR);
			inv.getStock(MAN_HOUR).add(optimalManhours);
			Quantity prod = prodFun.produce(inv);
			double profits = prod.getAmount() * stats.getGoodsMarketStats().getPriceBelief(POTATOE) - optimalCost;

			double profitShare = 1.0 - laborShare;
			double profits2 = (optimalCost - fixedCosts) / laborShare * profitShare - fixedCosts;
			System.out.println("Firm should use " + optimalManhours + " " + MAN_HOUR + " to produce " + prod + " and yield a profit of " + profits + " (" + profits2 + ")");

			double totalInput = manhours.getYesterday().getTotWeight();
			double perFirm = totalInput / optimalNumberOfFirms;
			if (perFirm > 0.0) {
				inv.getStock(MAN_HOUR).add(perFirm);
				inv.getStock(LAND).add(totalLand / optimalNumberOfFirms);
				double output = prodFun.produce(inv).getAmount() * optimalNumberOfFirms;
				System.out.println("With " + optimalNumberOfFirms + " firms the " + totalInput + " manhours could have produced " + output + " instead of "
						+ stats.getGoodsMarketStats().getStats(POTATOE).getYesterday().getTotWeight());
			}
			double altInput = 12;
			System.out.println("Using only " + altInput + " man-hours would yield a profit of " + getProfits(prodFun, stats, altInput));
			
			for (IAgent a: sim.getAgents().getAgents()){
				IStock land = a.getInventory().getStock(LAND);
				if (!land.isEmpty()){
					System.out.println(a + " owns " + land);
				}
			}
		} catch (PriceUnknownException e) {
			e.printStackTrace(out);
		}
	}
	
	private double getTotalLand(Collection<? extends IAgent> agents) {
		double totalLand = 0.0;
		for (IAgent a: agents){
			totalLand += a.getInventory().getStock(LAND).getAmount();
		}
		return totalLand;
	}

	private double getProfits(IProductionFunction prodFun, IStatistics sim, double inputAmount) throws PriceUnknownException {
		Inventory inv = new Inventory(getMoney(), new Stock(LAND, 100));
		double costs = inputAmount * sim.getGoodsMarketStats().getPriceBelief(MAN_HOUR);
		inv.getStock(MAN_HOUR).add(inputAmount);
		Quantity prod = prodFun.produce(inv);
		return prod.getAmount() * sim.getGoodsMarketStats().getPriceBelief(POTATOE) - costs;
	}
	
	public static void main(String[] args) {
		System.out.println("asdasd");
	}
	
}
