/**
 * Created by Luzius Meisser on Jun 19, 2017
 * Copyright: Meisser Economics AG, Zurich
 * Contact: luzius@meissereconomics.com
 *
 * Feel free to reuse this code under the MIT License
 * https://opensource.org/licenses/MIT
 */
package com.agentecon.firm;

import com.agentecon.agent.IAgentIdGenerator;
import com.agentecon.consumer.IMarketParticipant;
import com.agentecon.exercises.FarmingConfiguration;
import com.agentecon.finance.Producer;
import com.agentecon.firm.decisions.ExpectedRevenueBasedStrategy;
import com.agentecon.firm.decisions.IFinancials;
import com.agentecon.firm.decisions.IFirmDecisions;
import com.agentecon.firm.production.CobbDouglasProduction;
import com.agentecon.goods.IStock;
import com.agentecon.learning.MarketingDepartment;
import com.agentecon.market.IPriceMakerMarket;
import com.agentecon.market.IPriceTakerMarket;
import com.agentecon.market.IStatistics;
import com.agentecon.production.IProductionFunction;

public class Farm extends Producer implements IMarketParticipant {

	private static final double MINIMUM_TARGET_INPUT = 14;

	private MarketingDepartment marketing;
	private FinanceDepartment investment;
	private IFirmDecisions strategy;

	public Farm(IAgentIdGenerator id, IShareholder owner, IStock money, IStock land, IProductionFunction prodFun, IStatistics stats) {
		super(id, owner, prodFun, stats.getMoney());
		this.marketing = new MarketingDepartment(getMoney(), stats.getGoodsMarketStats(), getStock(FarmingConfiguration.MAN_HOUR), getStock(FarmingConfiguration.POTATOE));
		// this.finance = new FinanceDepartment(marketing.getFinancials(getInventory(), prodFun));
		this.strategy = new ExpectedRevenueBasedStrategy(prodFun.getWeight(FarmingConfiguration.MAN_HOUR).weight);
		this.investment = new FinanceDepartment((CobbDouglasProduction) prodFun, stats.getDiscountRate());
		getStock(land.getGood()).absorb(land);
		getMoney().absorb(money);
		assert getMoney().getAmount() > 0;
	}

	@Override
	public void offer(IPriceMakerMarket market) {
		double budget = calculateBudget();
		marketing.createOffers(market, this, budget);
	}
	
	@Override
	public void tradeGoods(IPriceTakerMarket market) {
		investment.invest(this, getInventory(), getFinancials(), market);
	}

	private double calculateBudget() {
		double defaultBudget = strategy.calcCogs(getFinancials());
		double minimumReasonableSpending = MINIMUM_TARGET_INPUT * marketing.getPriceBelief(FarmingConfiguration.MAN_HOUR);
		if (getMoney().getAmount() < minimumReasonableSpending) {
			return 0;
		} else {
			return Math.max(defaultBudget, minimumReasonableSpending);
		}
	}

	@Override
	public void adaptPrices() {
		marketing.adaptPrices();
	}

	private IFinancials getFinancials() {
		return marketing.getFinancials(getInventory(), getProductionFunction());
	}

	@Override
	protected double calculateDividends(int day) {
		return strategy.calcDividend(getFinancials());
	}

	private int daysWithoutProfit = 0;

	@Override
	public boolean considerBankruptcy(IStatistics stats) {
		super.considerBankruptcy(stats);
		double profits = getFinancials().getProfits();
		if (profits <= 0) {
			daysWithoutProfit++;
		} else {
			daysWithoutProfit = 0;
		}
		return daysWithoutProfit > 5 && stats.getRandomNumberGenerator().nextDouble() < 0.2;
		// return getMoney().getAmount() < 10.0; // we ran out of money, go bankrupt
	}

}
