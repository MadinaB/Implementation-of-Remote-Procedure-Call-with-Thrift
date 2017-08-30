package PasswordCrackerWorker;

import org.apache.thrift.TException;
import thrift.gen.PasswordCrackerWorkerService.PasswordCrackerWorkerService;
import java.util.concurrent.*;

import static PasswordCrackerWorker.PasswordCrackerUtil.findPasswordInRange;

class TerminationChecker {
    boolean isTerminated;

    TerminationChecker() {
        isTerminated = false;
    }

    public boolean isTerminated() {
        return isTerminated;
    }

    public void setTerminated() {
        isTerminated = true;
    }
}


public class PasswordCrackerWorkerServiceHandler implements PasswordCrackerWorkerService.Iface {
    static ConcurrentHashMap<String, TerminationChecker> terminationCheckerMap = new ConcurrentHashMap<>(); //  <jobId, TerminationChecker>
    int numberOfProcessor = Runtime.getRuntime().availableProcessors();
    ExecutorService workerPool = Executors.newFixedThreadPool(numberOfProcessor);

    /*
     * The startFindPasswordInRange is called by the master.
     * Return the result if findPasswordInRange task terminates.
     */
    @Override
    public String startFindPasswordInRange(long rangeBegin, long rangeEnd, String encryptedPassword) throws TException {

        String passwordOrNull = null;

        try {
            if (!terminationCheckerMap.containsKey(encryptedPassword)) {
                terminationCheckerMap.put(encryptedPassword, new TerminationChecker());
            }

            TerminationChecker terminationChecker = terminationCheckerMap.get(encryptedPassword);
            Future<String> workerFuture = workerPool.submit(() -> findPasswordInRange(rangeBegin, rangeEnd, encryptedPassword, terminationChecker));

           
            //get the result using Future class
            passwordOrNull=workerFuture.get();

        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
        catch (ExecutionException e) {
            e.printStackTrace();
        }
        return passwordOrNull;
    }

    /*
     * The reportTermination is called by the master.
     * it set the termination signal.
     */
    @Override
    public void reportTermination(String jobId) throws TException {


    	if (terminationCheckerMap.containsKey(jobId)) {
    		TerminationChecker tChecker=terminationCheckerMap.get(jobId);
    		tChecker.setTerminated();
            terminationCheckerMap.put(jobId, tChecker);
        }
         //

          /*When workers receive the termination signal and the corresponding
         job ID(or encryptedpassword) from Master, they must stop processing
         the job.
               Then the workers wait for other job if another job is not.
            */

    }

}

