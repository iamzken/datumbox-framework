/**
 * Copyright (C) 2013-2016 Vasilis Vryniotis <bbriniotis@datumbox.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datumbox.framework.core.machinelearning.regression;

import com.datumbox.framework.common.Configuration;
import com.datumbox.framework.common.dataobjects.Dataframe;
import com.datumbox.framework.common.interfaces.Trainable;
import com.datumbox.framework.common.persistentstorage.interfaces.DatabaseConnector;
import com.datumbox.framework.common.utilities.MapMethods;
import com.datumbox.framework.core.machinelearning.common.abstracts.AbstractTrainer;
import com.datumbox.framework.core.machinelearning.common.abstracts.modelers.AbstractRegressor;
import com.datumbox.framework.core.machinelearning.common.interfaces.StepwiseCompatible;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;


/**
 * Implementation of Stepwise Regression algorithm. The method takes as argument
 * an other regression model which uses to estimate the best model. This implementation
 * uses the backwards elimination algorithm.
 *
 * @author Vasilis Vryniotis <bbriniotis@datumbox.com>
 */
public class StepwiseRegression extends AbstractRegressor<StepwiseRegression.ModelParameters, StepwiseRegression.TrainingParameters>  {
    
    private transient AbstractRegressor mlregressor = null;
    
    /** {@inheritDoc} */
    public static class ModelParameters extends AbstractRegressor.AbstractModelParameters {
        private static final long serialVersionUID = 1L;

        /** 
         * @param dbc
         * @see AbstractTrainer.AbstractModelParameters#AbstractModelParameters(DatabaseConnector)
         */
        protected ModelParameters(DatabaseConnector dbc) {
            super(dbc);
        }
        
        //EMPTY Model parameters. It relies on the mlregressor DB instead        
    } 

    /** {@inheritDoc} */
    public static class TrainingParameters extends AbstractRegressor.AbstractTrainingParameters {
        private static final long serialVersionUID = 1L;
        
        //primitives/wrappers
        private Integer maxIterations = null;
        
        private Double aout = 0.05;
        
        //Classes
        private Class<? extends AbstractRegressor> regressionClass;

        //Parameter Objects
        private AbstractRegressor.AbstractTrainingParameters regressionTrainingParameters;

        //Field Getters/Setters

        /**
         * Getter for the maximum permitted iterations during training.
         * 
         * @return 
         */
        public Integer getMaxIterations() {
            return maxIterations;
        }

        /**
         * Setter for the maximum permitted iterations during training.
         * 
         * @param maxIterations 
         */
        public void setMaxIterations(Integer maxIterations) {
            this.maxIterations = maxIterations;
        }
        
        /**
         * Getter for the threshold of the maximum p-value; a variable must
         * have a p-value less or equal than the threshold to be retained in the 
         * model. Variables with higher p-value than this threshold are removed.
         * 
         * 
         * @return 
         */
        public Double getAout() {
            return aout;
        }
        
        /**
         * Setter for the threshold of the maximum p-value; a variable must
         * have a p-value less or equal than the threshold to be retained in the 
         * model. Variables with higher p-value than this threshold are removed.
         * 
         * @param aout 
         */
        public void setAout(Double aout) {
            this.aout = aout;
        }
        
        /**
         * Getter for the class of the regression algorithm that is used internally
         * by the Stepwise Regression. The regressor must implement the StepwiseCompatible
         * interface in order to be used in the analysis.
         * 
         * @return 
         */
        public Class<? extends AbstractRegressor> getRegressionClass() {
            return regressionClass;
        }
        
        /**
         * Setter for the class of the regression algorithm that is used internally
         * by the Stepwise Regression. The regressor must implement the StepwiseCompatible
         * interface in order to be used in the analysis.
         * 
         * @param regressionClass 
         */
        public void setRegressionClass(Class<? extends AbstractRegressor> regressionClass) {
            if(!StepwiseCompatible.class.isAssignableFrom(regressionClass)) {
                throw new IllegalArgumentException("The regression model is not Stepwise Compatible.");
            }
            this.regressionClass = regressionClass;
        }
        
        /**
         * Getter for the training parameters of the regression algorithm that is
         * used internally by the Stepwise Regression.
         * 
         * @return 
         */
        public AbstractRegressor.AbstractTrainingParameters getRegressionTrainingParameters() {
            return regressionTrainingParameters;
        }
        
        /**
         * Setter for the training parameters of the regression algorithm that is
         * used internally by the Stepwise Regression.
         * 
         * @param regressionTrainingParameters 
         */
        public void setRegressionTrainingParameters(AbstractRegressor.AbstractTrainingParameters regressionTrainingParameters) {
            this.regressionTrainingParameters = regressionTrainingParameters;
        }
        
    }


    /**
     * @param dbName
     * @param conf
     * @param trainingParameters
     * @see AbstractTrainer#AbstractTrainer(String, Configuration, AbstractTrainer.AbstractTrainingParameters)
     */
    public StepwiseRegression(String dbName, Configuration conf, TrainingParameters trainingParameters) {
        super(dbName, conf, trainingParameters);
    }

    /**
     * @param dbName
     * @param conf
     * @see AbstractTrainer#AbstractTrainer(java.lang.String, Configuration)
     */
    public StepwiseRegression(String dbName, Configuration conf) {
        super(dbName, conf);
    }

    /** {@inheritDoc} */
    @Override
    public void delete() {
        if(mlregressor != null) {
            mlregressor.delete();
            mlregressor = null;
        }
        
        super.delete();
    }
         
    /** {@inheritDoc} */
    @Override
    public void close() {
        if(mlregressor != null) {
            mlregressor.close();
            mlregressor = null;
        }
        
        super.close();
    }

    /** {@inheritDoc} */
    @Override
    protected void _predict(Dataframe newData) {
        if(mlregressor==null) {
            mlregressor = Trainable.newInstance(knowledgeBase.getTrainingParameters().getRegressionClass(), dbName, knowledgeBase.getConf());
        }
        
        mlregressor.predict(newData);
    }
    
    /** {@inheritDoc} */
    @Override
    protected void _fit(Dataframe trainingData) {
        TrainingParameters trainingParameters = knowledgeBase.getTrainingParameters();
        
        Integer maxIterations = trainingParameters.getMaxIterations();
        if(maxIterations==null) {
            maxIterations = Integer.MAX_VALUE;
        }
        double aOut = trainingParameters.getAout();
        
        //copy data before starting
        Dataframe copiedTrainingData = trainingData.copy();
        
        //backword elimination algorithm
        for(int iteration = 0; iteration<maxIterations ; ++iteration) {
            
            Map<Object, Double> pvalues = runRegression(copiedTrainingData);
            
            if(pvalues.isEmpty()) {
                break; //no more features
            }
            
            //fetch the feature with highest pvalue, excluding constant
            pvalues.remove(Dataframe.COLUMN_NAME_CONSTANT);
            Map.Entry<Object, Double> maxPvalueEntry = MapMethods.selectMaxKeyValue(pvalues);
            //pvalues=null;
            
            if(maxPvalueEntry.getValue()<=aOut) {
                break; //nothing to remove, the highest pvalue is less than the aOut
            }
            
            
            Set<Object> removedFeatures = new HashSet<>();
            removedFeatures.add(maxPvalueEntry.getKey());
            copiedTrainingData.dropXColumns(removedFeatures);
            //removedFeatures = null;
            
            if(copiedTrainingData.xColumnSize()==0) {
                break; //if no more features exit
            }
        }
        
        //once we have the dataset has been cleared from the unnecessary columns train the model once again
        mlregressor = Trainable.newInstance(
            knowledgeBase.getTrainingParameters().getRegressionClass(),
            dbName,
            knowledgeBase.getConf(),
            knowledgeBase.getTrainingParameters().getRegressionTrainingParameters()
        );
        
        mlregressor.fit(copiedTrainingData);
        copiedTrainingData.delete();
    }

    private Map<Object, Double> runRegression(Dataframe trainingData) {
        //initialize algorithm
        mlregressor = Trainable.newInstance(
                knowledgeBase.getTrainingParameters().getRegressionClass(),
                dbName,
                knowledgeBase.getConf(),
                knowledgeBase.getTrainingParameters().getRegressionTrainingParameters()
        );

        //train the regressor
        mlregressor.fit(trainingData);

        //get pvalues
        Map<Object, Double> pvalues = ((StepwiseCompatible)mlregressor).getFeaturePvalues();
        mlregressor.delete();
        
        return pvalues;
    }
}
