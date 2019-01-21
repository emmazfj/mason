package sim.util.sweep;

import sim.engine.*;

import java.io.*;
import java.util.*;
import sim.util.*;
import ec.util.*;

public class ParameterSweep 
    {
    // the master file 
    PrintWriter writer;
    Object[] writerLock = new Object[0];
    public void println(String val) { synchronized(writerLock) { writer.println(val); } }

    // Value Bounds for independent variables
    double minValues[];
    double maxValues[];
    
    //total number of steps between and including min->max value
    int numSteps[];
    
    // current job count, don't play with this
    int jobCount = 0;
    
    // Names of the independent and dependent variables  -- FIXME: we could extract this from Properties as well
    String independentNames[];
    String dependentNames[];

    // For each independent or dependent variable, the index into a Properties on SimState where that variable is stored   
    int independentIndexes[];
    int dependentIndexes[];
    int nStep[];
    
    // For each dependent variable, whether we want to main the avg, min, or max over the simulation time, and whether we should record every step
    // FIXME: everyStep should indicate skips in steps
    boolean recordAverage[];
    boolean recordMin[];
    boolean recordMax[];
    boolean everyStep[];

    int numRepeats = 1;
    int numThreads = 1;

    Class modelClass;
    String[] args;
    
    // Number of simulation steps?
    int simulationSteps;
    
    /// FIXME We need to set this in the constructor
    long baseSeed = 100;

    // This is an arraylist of arraylists of doubles, recursively generated, for each combination of values of our independent variables
    ArrayList<ArrayList<Double>> allIndependentVariableValueCombinations = new ArrayList<ArrayList<Double>>();
    
    public boolean stop;

    public ParameterSweep(ParameterDatabase db) throws ClassNotFoundException
        {
        populateParameterSweep(db);
        }
                    
    // this constructor is used by the GUI.

    public static void main(String[] args) throws  IOException, ClassNotFoundException
        {
        ParameterSweep sweep = new ParameterSweep(new ParameterDatabase(new File(new File(args[0]).getAbsolutePath()), args));
        sweep.args = args;
        sweep.runSweepFromParamDatabase();
        }

    public void runSweepFromParamDatabase() throws ClassNotFoundException 
        {
        generateAllIndependentVariableValueCombinations(new ArrayList<Double>());
        
        Thread[] threads = new Thread[numThreads];
        for(int i = 0; i < threads.length; i++) 
            {
            threads[i] = new Thread()
                {
                public void run()
                    {
                    ParameterSweepSimulationJob job;
                    while ((job = (ParameterSweepSimulationJob)getNextJob()) != null) 
                        {
                        if (stop) 
                            {
                            break;
                            }
                        job.run();
                        }
                    }
                };
            threads[i].start();
            }
        for(int i = 0; i<threads.length; i++) 
            {
            try 
                {
                threads[i].join();
                }
            catch(InterruptedException e)
                {
                e.printStackTrace();
                }
            }
        writer.close();
        }
    
    //takes a parameter src.main.java.sim.util.sweep and populates it based on a parameter database
    public void populateParameterSweep(ParameterDatabase pd) throws ClassNotFoundException 
        {
        String appPath = ((String)(pd.getStringWithDefault(new Parameter("app"), null, "bad"))).replace("/",".");

        modelClass = Class.forName(appPath);
        minValues = getArrayByParameter(pd,new Parameter("min"));
        maxValues = getArrayByParameter(pd,new Parameter("max"));
        numSteps = getIntegerArrayByParameter(pd,new Parameter("steps"));
        independentNames = ((String)(pd.getStringWithDefault(new Parameter("independent"), null, "bad"))).split(",");
        dependentNames = ((String)(pd.getStringWithDefault(new Parameter("dependent"), null, "bad"))).split(",");
        simulationSteps = Integer.parseInt(((String)(pd.getStringWithDefault(new Parameter("simulationSteps"), null, "bad"))));
        everyStep = getBooleanArrayByParameter(pd,new Parameter("everyStep"),dependentNames.length);
        nStep = getIntegerArrayByParameter(pd, new Parameter("nStep"));
        recordAverage = getBooleanArrayByParameter(pd,new Parameter("avg"),dependentNames.length);
        recordMin = getBooleanArrayByParameter(pd,new Parameter("recordMin"),dependentNames.length);
        recordMax = getBooleanArrayByParameter(pd,new Parameter("recordMax"),dependentNames.length);
        numRepeats = Integer.parseInt(((String)(pd.getStringWithDefault(new Parameter("numRepeats"), null, "bad"))));
        numThreads = Integer.parseInt(((String)(pd.getStringWithDefault(new Parameter("threads"), null, "bad"))));

        try
            {
            writer = new PrintWriter(new FileWriter(((String)(pd.getStringWithDefault(new Parameter("out"), null, "bad")))));
            }
        catch (IOException e)
            {
            //// FIXME, we have to handle this
            e.printStackTrace();
            }
                
        SimState simState = newInstance(0, modelClass);
        sim.util.Properties properties = sim.util.Properties.getProperties(simState);
        initializeIndexes(properties);
        }
    
    // Populate all permutations of settings. Recursive.
    public void generateAllIndependentVariableValueCombinations(ArrayList<Double> current)
        {
        if (current.size() == independentIndexes.length)
            {
            allIndependentVariableValueCombinations.add((ArrayList<Double>)current.clone());
            return;
            }

        int index = current.size();
        double increment = (maxValues[index]-minValues[index])/(numSteps[index]-1);
        for(int  i = 0; i<numSteps[current.size()]; i++)
            {
            current.add(minValues[index]+i*increment);
            generateAllIndependentVariableValueCombinations(current);
            current.remove(current.size()-1);
            }
        }

    // Takes the property names, and gets the property indexes
    // FIXME: What does this do precisely?
    private void initializeIndexes(sim.util.Properties p) 
        {
        independentIndexes = new int[independentNames.length];
        for(int i = 0; i<independentNames.length; i++)
            {
            boolean success = false;
            for(int a = 0; a<p.numProperties(); a++)
                {
                if (p.getName(a).equals(independentNames[i]))
                    {
                    //System.err.println("setting independent index at " + i + " to " + a + " which should be " + p.getName(a));
                    independentIndexes[i] = a;
                    success = true;
                    }
                }
            if (!success)
                {
                //System.err.println("Parameter did not exist! " + independentNames[i]);
                System.exit(1);
                }
            }

        dependentIndexes = new int[dependentNames.length];
        for(int i = 0; i<dependentNames.length; i++)
            {
            boolean success = false;
            for(int a = 0; a<p.numProperties(); a++)
                {
                if (p.getName(a).equals(dependentNames[i]))
                    {
                    dependentIndexes[i] = a;
                    success = true;
                    }
                }
            if (!success)
                {
                //System.err.println("Parameter did not exist! " + dependentNames[i]);
                System.exit(1);
                }
            }
        }
    
    //converts a list of doubles in the parameter database to a double array
    private double[] getArrayByParameter(ParameterDatabase pd, Parameter par) 
        {
        String nums = (String)pd.getStringWithDefault(par, null, "bad");
        String numsArray[] = nums.split(",");

        double[] result = new double[numsArray.length];

        for(int i = 0; i<numsArray.length; i++)
            {
            if (numsArray[i].equals("true"))
                {
                result[i] = 1.0;
                }
            else if (numsArray[i].equals("false"))
                {
                result[i] = 0.0;
                }
            else 
                {
                try 
                    {
                    result[i] = Double.parseDouble(numsArray[i]);
                    }
                catch(Exception e)
                    {
                    //System.err.println("INVALID PARAMETER");
                    e.printStackTrace();
                    System.exit(1);

                    }
                }
            }
        return result;
        }

    private boolean[] getBooleanArrayByParameter(ParameterDatabase pd, Parameter par, int numExpected) 
        {
        String nums = (String)pd.getStringWithDefault(par, null, "bad");
        boolean[] result = new boolean[numExpected];
        if (nums.equals("bad"))
            {
            for(int i = 0; i<result.length; i++)
                {
                result[i]  = false;
                }
            }
        String numsArray[] = nums.split(",");

        for(int i = 0; i<numsArray.length; i++)
            {
            try 
                {
                result[i] = Boolean.parseBoolean(numsArray[i]);
                }
            catch(Exception e) 
                {
                //System.err.println("INVALID PARAMETER");
                e.printStackTrace();
                System.exit(1);
                }
            }
        return result;
        }
    
    private int[] getIntegerArrayByParameter(ParameterDatabase pd, Parameter par) 
        {
        String nums = (String)pd.getStringWithDefault(par, null, "bad");
        String numsArray[] = nums.split(",");
        int[] result = new int[numsArray.length];
        for(int i = 0; i<numsArray.length; i++)
            {
            result[i] = Integer.parseInt(numsArray[i]);
            }
        return result;
        }
    
    //creates a new simstate with reflection
    public SimState newInstance(long seed, Class c)
        {
        try
            {
            return (SimState)(c.getConstructor(new Class[] { Long.TYPE }).newInstance(new Object[] { Long.valueOf(seed) } ));
            }
        catch (Exception e)
            {
            throw new RuntimeException("Exception occurred while trying to construct the simulation " + c + "\n" + e);
            }
        }

    Object[] nextJobLock = new Object[0];
    public ParameterSweepSimulationJob getNextJob() 
        {
        synchronized(nextJobLock)
            {
            if (jobCount < allIndependentVariableValueCombinations.size() * numRepeats)              // I think this means we're done?
                {
                /// FIXME: is the integer division here correct?
                ParameterSweepSimulationJob j = new ParameterSweepSimulationJob(allIndependentVariableValueCombinations.get(jobCount / numRepeats), this, jobCount);
                jobCount++;
                return j;
                }
            else
                return null;
            }
        }

    }



class ParameterSweepSimulationJob
    {
    ArrayList<Double> settings;
    ParameterSweep sweep;
    SimState simState;
    sim.util.Properties properties;
    StringBuilder builder = new StringBuilder();
    int jobCount;

    double[] averages;
    double[] mins;
    double[] maxes;
        
    public ParameterSweepSimulationJob(ArrayList<Double> settings, ParameterSweep sweep, int jobCount)
        {
        this.jobCount = jobCount;
        this.sweep = sweep;
        simState = sweep.newInstance(sweep.baseSeed + jobCount, sweep.modelClass);
        properties = sim.util.Properties.getProperties(simState);
        for(int i = 0; i< sweep.independentIndexes.length; i++)
            {
            int index =i;
            String type = properties.getType(sweep.independentIndexes[index]).toString();

            if (type.equals("double")) 
                {
                properties.setValue(sweep.independentIndexes[index], settings.get(index));
                }
            else if (type.equals("int")) 
                {
                properties.setValue(sweep.independentIndexes[index], (int)Math.round(settings.get(index)));
                }
            else if (type.equals("boolean")) 
                {
                properties.setValue(sweep.independentIndexes[index], Math.round(settings.get(index)) == 1);
                }
            else
                {
                //System.err.println("Independent: unsupported type " + properties.getType(src.main.java.sim.util.sweep.independentIndexes[index]).toString() +  " on index " + index + " which should be..." + properties.getName(src.main.java.sim.util.sweep.independentIndexes[index]));
                throw new RuntimeException("Unsupported type");
                }
            }

        averages = new double[sweep.dependentIndexes.length];
        mins = new double[sweep.dependentIndexes.length];
        maxes = new double[sweep.dependentIndexes.length];

        System.err.println("running simstate with settings: " + settings);
        }
    

    public void record(boolean start)
        {
        for(int i = 0; i < sweep.dependentIndexes.length; i++)
            {
            double value = getPropertyValueAsDouble(properties, sweep.dependentIndexes[i]);

            if (sweep.recordAverage[i])
                {
                averages[i] += value;
                }
                
            if (sweep.recordMin[i])
                {
                if (mins[i] > value || start)
                    mins[i] = value;
                }
                
            if (sweep.recordMax[i])
                {
                if (maxes[i] < value || start)
                    maxes[i] = value;
                }
                
            //// FIXME We are not skipping steps per the GUI

            if (sweep.everyStep[i] || (i != 0 && (sweep.nStep[i] % i == 0))) 
                {
                    builder.append(", " + value);
                }
            }
        }
         //could be done more efficiently instead of with 4 for-loops?        
    public void recordFinal(int numSteps)
        {
        String str = "";
        for(int i = 0; i < sweep.independentNames.length; i++) 
        {
            str = str + ", " + sweep.independentNames[i];
        }
        for(int i = 0; i < sweep.dependentNames.length; i++) 
        {
            str = str + ", " + sweep.dependentNames[i]; i++;
        }

        for(int i = 0; i < sweep.dependentIndexes.length; i++) 
        {
            str = str + ", " + properties.getValue(sweep.dependentIndexes[i]).toString();
        }
                
        for(int i = 0; i < sweep.dependentIndexes.length; i++)
            {
            // Record last for sure
            str = str + ", " + properties.getValue(sweep.dependentIndexes[i]).toString();
                        
            // record average, min, max
            if (sweep.recordAverage[i]) 
                {
                str = str + ", avg: " + averages[i] / numSteps;
                }
                        
            if (sweep.recordMin[i]) 
                {
                str = str + ", min: " + mins[i];
                }
            if (sweep.recordMax[i])
                {
                str = str + ", max: " + maxes[i];
                }
            }
                        
        if (builder.length() > 0)
            {
            str = str + ", STEPS" + builder.toString();
            }
                        
        sweep.println(str);
        }
    
    public void run() 
        {
        // Run the simulation
        simState.start();
        for(int i = 0; i< sweep.simulationSteps; i++)
            {
                if (sweep.stop)
                    {
                    simState.finish();  // don't bother to record final here
                    return;
                }
                simState.schedule.step(simState);
                record(i == 0);
            }
        
        // at this point we weren't stopped, so clean up properly
        simState.finish();
        recordFinal(sweep.simulationSteps);
        }

    public double getPropertyValueAsDouble(sim.util.Properties properties, int dependentIndex) 
        {
        double dValue = 0.0;
        int propertyIndex = dependentIndex;
        String type = properties.getType(propertyIndex).toString();

        if (type.equals("double")) 
            {
            dValue = (Double)properties.getValue(propertyIndex);
            }
        else if (type.equals("int")) 
            {
            dValue = ((Integer)properties.getValue(propertyIndex)).doubleValue();
            }
        else if (type.equals("boolean")) 
            {
            dValue = ((Boolean)properties.getValue(propertyIndex)) ? 1  : 0;
            }
        else
            {
            //System.err.println("Independent: unsupported type " + properties.getType(propertyIndex).toString());
            System.exit(1);
            }
        return dValue;
        }
    }