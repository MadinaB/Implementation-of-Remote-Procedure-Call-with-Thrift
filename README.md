# Implementation-of-Remote-Procedure-Call-with-Thrift
1) Client invokes a method in Master (via RPC) and gives the encrypted password to compute the password.    
This RPC call to Master does not return until Master sends back the password. 

2) Master, upon receiving the encrypted password from Client, requests Workers to start computing the password. 

3) Workers receive the encrypted password as well as their assigned ranges of candidate passwords from Master.    
The workers compute the password in parallel; if one of the workers find the password, it sends the password to back to Master.    
Then Master singals other workers to stop the computation.     
4) Master returns the password back to Client.
