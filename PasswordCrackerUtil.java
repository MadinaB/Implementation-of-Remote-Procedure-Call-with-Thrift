package PasswordCrackerWorker;

import org.apache.thrift.TException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

import static PasswordCrackerWorker.PasswordCrackerConts.PASSWORD_CHARS;
import static PasswordCrackerWorker.PasswordCrackerConts.PASSWORD_LEN;

public class PasswordCrackerUtil {

    private static MessageDigest getMessageDigest() {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            throw new RuntimeException("Cannot use MD5 Library:" + e.getMessage());
        }
    }

    private static String encrypt(String password, MessageDigest messageDigest) {
        messageDigest.update(password.getBytes());
        byte[] hashedValue = messageDigest.digest();
        return byteToHexString(hashedValue);
    }

    private static String byteToHexString(byte[] bytes) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            String hex = Integer.toHexString(0xFF & bytes[i]);
            if (hex.length() == 1) {
                builder.append('0');
            }
            builder.append(hex);
        }
        return builder.toString();
    }

    /*
     * The findPasswordInRange method finds the password.
     * if it finds the password, it set the termination for transferring signal to master and returns password to caller.
     */
    public static String findPasswordInRange(long rangeBegin, long rangeEnd, String encryptedPassword, TerminationChecker terminationChecker) throws TException, InterruptedException {
        
        String instance=null;
        MessageDigest messageDgst= getMessageDigest();
        int[] candidateChars=new int[PASSWORD_LEN];//PASSWORD_LEN
        transformDecToBase36(rangeBegin,candidateChars);
        for(long i=rangeBegin;i<=rangeEnd;i++){                     //Include bounds.
            instance = transformIntoStr(candidateChars);
            String encryptedInstance=encrypt(instance, messageDgst);
       //       System.out.println(instance+" "+encryptedInstance+" "+encryptedPassword
       //             +" "+(encryptedInstance==encryptedPassword));
            if(!(encryptedInstance.equals(encryptedPassword)))
            {instance=null;}
            else{ return instance;}                                             // Password is found.
            getNextCandidate(candidateChars);
            //if(isEarlyTermination==true){if(passwordFuture.isDone()){return null;}}
        }
        return null;

        
        
        
        
        
        
        
    }

    /* ###  transformDecToBase36  ###
     * The transformDecToBase36 transforms decimal into numArray that is base 36 number system
     * If you don't understand, refer to the homework01 overview
    */
    private static void transformDecToBase36(long numInDec, int[] numArrayInBase36) {
        
            long n=numInDec;
            for(int i=0;i<PASSWORD_LEN;i++){//PasswordCrackerConts.PASSWORD_LEN  ?
                numArrayInBase36[PASSWORD_LEN-i-1]=(int)n%36;// PasswordCrackerConts.PASSWORD_LEN   ?
                n=n/36;
            }
    }

    
    private static boolean increment(int[] arr, int index) {
            arr[index]=arr[index]+1;
            if(arr[index]<=35){return false;}// no need in recurse
            else{
                arr[index]=0;
                return true;}
    }
    //  ### getNextCandidate ###
    private static void getNextCandidate(int[] arr) {
         int i=arr.length-1;
        boolean recurse=increment(arr,i);
        while(recurse){
            if(i==0){break;}
            i=i-1; recurse=increment(arr,i);
        }
    }

    private static String transformIntoStr(int[] chars) {
        char[] password = new char[chars.length];
        for (int i = 0; i < password.length; i++) {
            password[i] = PASSWORD_CHARS.charAt(chars[i]);
        }
        return new String(password);
    }
}
