package PasswordCrackerMaster;

import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.async.TAsyncClientManager;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TNonblockingSocket;
import org.apache.thrift.transport.TSocket;

//import PasswordCrackerWorker.TerminationChecker;
import thrift.gen.PasswordCrackerMasterService.PasswordCrackerMasterService;
import thrift.gen.PasswordCrackerWorkerService.PasswordCrackerWorkerService;

import java.util.concurrent.ExecutionException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static PasswordCrackerMaster.PasswordCrackerConts.TOTAL_PASSWORD_RANGE_SIZE;
import static PasswordCrackerMaster.PasswordCrackerConts.SUB_RANGE_SIZE;
import static PasswordCrackerMaster.PasswordCrackerConts.WORKER_PORT;
import static PasswordCrackerMaster.PasswordCrackerMasterServiceHandler.jobInfoMap;
import static PasswordCrackerMaster.PasswordCrackerMasterServiceHandler.workersAddressList;

public class PasswordCrackerMasterServiceHandler implements PasswordCrackerMasterService.Iface {
    public static List<TSocket> workersSocketList = new LinkedList<>();  //Connected Socket
    public static List<String> workersAddressList = new LinkedList<>(); // Connected WorkerAddress
    public static ConcurrentHashMap<String, PasswordDecrypterJob> jobInfoMap = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String, Long> latestHeartbeatInMillis = new ConcurrentHashMap<>(); // <workerAddress, time>
    public static ExecutorService workerPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);
    public static ScheduledExecutorService heartBeatCheckPool = Executors.newScheduledThreadPool(1);

    /*
     * The decrypt method create the job and put the job with jobId (encrypted Password) in map.
     * And call the requestFindPassword and if it finds the password, it return the password to the client.
     */
    @Override
    public String decrypt(String encryptedPassword)
            throws TException {
        PasswordDecrypterJob decryptJob = new PasswordDecrypterJob();
        jobInfoMap.put(encryptedPassword, decryptJob);

        
        //call the requestFindPassword
        String decryptedPassword=null;
        // it can be requested by client after some process is done and workersAddressList is changed
        long range=(TOTAL_PASSWORD_RANGE_SIZE + workersAddressList.size() - 1) / workersAddressList.size();
        long rangeBegin=0;
        requestFindPassword(encryptedPassword, rangeBegin, range);
        try{
        decryptedPassword =decryptJob.getPassword();
        } catch (InterruptedException e) {
                e.printStackTrace();
        } catch (ExecutionException e) {
                e.printStackTrace();
        }
        return decryptedPassword;



    }

 /*
     * The reportHeartBeat receives the heartBeat from workers.
     * Consider the checkHeartBeat method and use latestHeartbeatInMillis map.
    */
    @Override
    public void reportHeartBeat(String workerAddress)
            throws TException {


        latestHeartbeatInMillis.put(workerAddress, System.currentTimeMillis());
       
        //latestHeartbeatInMillis
    }

    /*
     * The requestFindPassword requests workers to find password using RPC in asynchronous way.
    */

public static void requestFindPassword(String encryptedPassword, long rangeBegin, long subRangeSize) {
        PasswordCrackerWorkerService.AsyncClient worker = null;
        FindPasswordMethodCallback findPasswordCallBack = new FindPasswordMethodCallback(encryptedPassword);
        try {
            int workerId = 0;
            for (String workerAddress : workersAddressList) {
                //subRangeSize = (TOTAL_PASSWORD_RANGE_SIZE + workersAddressList.size() - 1) / workersAddressList.size();
                long subRangeBegin = rangeBegin + (workerId * subRangeSize);
                long subRangeEnd = subRangeBegin + subRangeSize;

                worker = new PasswordCrackerWorkerService.AsyncClient(new TBinaryProtocol.Factory(), new TAsyncClientManager(), new TNonblockingSocket(workerAddress, WORKER_PORT));
                worker.startFindPasswordInRange(subRangeBegin, subRangeEnd, encryptedPassword, findPasswordCallBack);
                workerId++;
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        catch (TException e) {
            e.printStackTrace();
        }
    }

/*
     * The redistributeFailedTask distributes the dead workers's job (or a set of possible password) to active workers.
     *
     * Check the checkHeartBeat method
     */
    public static void redistributeFailedTask(ArrayList<Integer> failedWorkerIdList) {
        
        // HashSet<String> failedWorkersAddressList = new HashSet<>();
         List<String> failedWorkersAddressList = new LinkedList<>();
         // Save failedWorkersAddresses
         for (int x=0; x<failedWorkerIdList.size(); x++){
                  int workerId=failedWorkerIdList.get(x);
                  String workerAddress =workersAddressList.get(workerId);
                  failedWorkersAddressList.add(workerAddress);
         }
         // Delete failedWorkersAddress from workerAdresses
         for (int x=0; x< failedWorkersAddressList.size(); x++){
                 String workerAddress=failedWorkersAddressList.get(x);
             workersAddressList.remove(workerAddress);
         }
         // call requestFindPassword for new workerAdresses and for all not decrypted yet passwords.
         long rangeBegin=0;
         long subRangeSize=subRangeSize = (TOTAL_PASSWORD_RANGE_SIZE + workersAddressList.size() - 1) / workersAddressList.size();
         for (String encryptedPassword : jobInfoMap.keySet()) {
                 requestFindPassword(encryptedPassword, rangeBegin, subRangeSize);
        }
    }

 /*
     *  If the master didn't receive the "HeartBeat" in 5 seconds from any workers,
     *  it considers the workers that didn't send the "HeartBeat" as dead.
     *  And then, it redistributes the dead workers's job in other alive workers
     *
     *  hint : use latestHeartbeatinMillis, workerAddressList
     *
     *  you must think about when several workers is dead.
     *
     *  and use the workerPool
     */
    public static void checkHeartBeat() {
       
        int workerId = 0;
        final long thresholdAge = 5_000;

        ArrayList<Integer> failedWorkerIdList = new ArrayList<>();
        for (String workerAddress : workersAddressList) {
                Long lastSeenAt=latestHeartbeatInMillis.get(workerAddress);
                Long currentTime= System.currentTimeMillis();
                if( currentTime- thresholdAge>lastSeenAt){failedWorkerIdList.add(workerId);}
                workerId++;

        }
        redistributeFailedTask(failedWorkerIdList);

      

    }
}



//CallBack
class FindPasswordMethodCallback implements AsyncMethodCallback<PasswordCrackerWorkerService.AsyncClient.startFindPasswordInRange_call> {
    private String jobId;

    FindPasswordMethodCallback(String jobId) {
        this.jobId = jobId;
    }

    /*
     *  if the returned result from worker is not null, it completes the job.
     *  and call the jobTermination method
     */
    @Override
    public void onComplete(PasswordCrackerWorkerService.AsyncClient.startFindPasswordInRange_call startFindPasswordInRange_call) {
        try {
            String findPasswordResult = startFindPasswordInRange_call.getResult();
            
            if(findPasswordResult!=null){
               

                jobInfoMap.get(jobId).setPassword(findPasswordResult);

                jobTermination(jobId);

            }
        }
        catch (TException e) {
            e.printStackTrace();
        }
    }



  @Override
    public void onError(Exception e) {
        System.out.println("Error : startFindPasswordInRange of FindPasswordMethodCallback");
    }

    /*
     *  The jobTermination transfer the termination signal to workers in asynchronous way
     */
    private void jobTermination(String jobId) {
        try {
            PasswordCrackerWorkerService.AsyncClient worker = null;
            for (String workerAddress : workersAddressList) {
                worker = new PasswordCrackerWorkerService.
                        AsyncClient(new TBinaryProtocol.Factory(), new TAsyncClientManager(), new TNonblockingSocket(workerAddress, WORKER_PORT));
               

 //               worker.reportTermination(jobId, null);

 worker.reportTermination(jobId, new AsyncMethodCallback<PasswordCrackerWorkerService.AsyncClient.reportTermination_call>(){public void onComplete(PasswordCrackerWorkerService.AsyncClient.reportTermination_call empty) {}public void onError(Exception e) {}});


                 jobInfoMap.remove(jobId);





            }
        }
        catch (TException e) {
            e.printStackTrace();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
}