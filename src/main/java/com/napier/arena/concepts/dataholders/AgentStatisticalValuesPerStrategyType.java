package com.napier.arena.concepts.dataholders;

import com.napier.arena.concepts.AgentContact;
import com.napier.arena.types.AgentStrategyType;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

/**
 * Calculates and contains statistical data about a simulation run.
 * This class was converted from the original ArrayList implementation in ResourceExchangeArena.
 *
 * @see <a href="https://github.com/NathanABrooks/ResourceExchangeArena/blob/master/src/resource_exchange_arena/CalculateSatisfaction.java">ResourceExchangeArena</a>
 *
 * @author László Tárkányi
 */
public class AgentStatisticalValuesPerStrategyType implements Serializable {
    private final double upperQuarter;
    private final double lowerQuarter;
    private final double ninetyFifthPercentile;
    private final double max;
    private final double min;
    private final double median;

    public AgentStatisticalValuesPerStrategyType(ArrayList<AgentContact> householdAgentContacts, AgentStrategyType agentStrategyType) {
        ArrayList<Double> agentSatisfactions = new ArrayList<>();

        for (AgentContact agentContact : householdAgentContacts) {
            if (agentContact.getType() == agentStrategyType) {
                agentSatisfactions.add(agentContact.getCurrentSatisfaction());
            }
        }

        /*
        The following code snippet was derived from ResourceExchangeArena, the original model this project is based on.
        See more: https://github.com/NathanABrooks/ResourceExchangeArena/blob/master/src/resource_exchange_arena/Day.java
        */

        Collections.sort(agentSatisfactions);

        int size = agentSatisfactions.size();
        double uq;
        double lq;
        double ninetyfifth;
        double max = size != 0 ? agentSatisfactions.get(size - 1) : 0;
        double min = size != 0 ? agentSatisfactions.getFirst() : 0;
        double median;

        double[] satArray = new double [size];
        int i = 0;
        for (double a : agentSatisfactions) {
            satArray[i] = a;
            i++;
        }

        double[] lqSet;
        double[] uqSet;

        if (size != 0) {
            if (size % 2 == 1) {
                median = satArray[size / 2];
                lqSet = Arrays.copyOfRange(satArray, 0, (size / 2));
                uqSet = Arrays.copyOfRange(satArray, (size / 2) + 1, size);
            } else {
                median = (satArray[size / 2] + satArray[(size / 2) - 1]) / 2;
                lqSet = Arrays.copyOfRange(satArray, 0, size / 2);
                uqSet = Arrays.copyOfRange(satArray, (size / 2), satArray.length);
            }

            if (lqSet.length % 2 == 1) {
                lq = lqSet[lqSet.length / 2];
                uq = 0!= uqSet.length ? uqSet[uqSet.length / 2] : 0;
            } else {
                lq = 0 != lqSet.length ? (lqSet[lqSet.length / 2] + lqSet[(lqSet.length / 2) - 1]) / 2 : 0;
                uq = 0 != uqSet.length ? (uqSet[uqSet.length / 2] + uqSet[(uqSet.length / 2) - 1]) / 2 : 0;
            }
        } else {
            median = 0;
            uq = 0;
            lq = 0;
        }

        ninetyfifth = size != 0 ? percentile(satArray) : 0;

        this.upperQuarter = uq;
        this.lowerQuarter = lq;
        this.ninetyFifthPercentile = ninetyfifth;
        this.max = max;
        this.min = min;
        this.median = median;
    }

    public double getUpperQuarter() {
        return upperQuarter;
    }

    public double getLowerQuarter() {
        return lowerQuarter;
    }

    public double getNinetyFifthPercentile() {
        return ninetyFifthPercentile;
    }

    public double getMax() {
        return max;
    }

    public double getMin() {
        return min;
    }

    public double getMedian() {
        return median;
    }

    /**
     * Use linear interpolation to calculate a percentile from an array of data.
     *
     * @see <a href="https://github.com/NathanABrooks/ResourceExchangeArena/blob/master/src/resource_exchange_arena/CalculateSatisfaction.java">ResourceExchangeArena</a>
     *
     * @param xs Array of values from which the percentile is calculated.
     * @return Double value of the percentile requested.
     */
    private static double percentile(double[] xs) {
        // The sorted elements in X are taken as the 100(0.5/n)th, 100(1.5/n)th, ..., 100([n – 0.5]/n)th percentiles.
        int i = (int) (95 * xs.length / 100.0 - 0.5);

        // Linear interpolation uses linear polynomials to find yi = f(xi), the values of the underlying function
        // Y = f(X) at the points in the vector or array x. Given the data points (x1, y1) and (x2, y2), where
        // y1 = f(x1) and y2 = f(x2), linear interpolation finds y = f(x) for a given x between x1 and x2 as follows:
        return i != (xs.length - 1) ? xs[i] + (xs[i + 1] - xs[i]) * (95 / 100.0 - (i + 0.5) / xs.length) / ((i + 1.5) / xs.length - (i + 0.5) / xs.length) : xs[i];
    }
}