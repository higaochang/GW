mvn clean install package  
cd target  
java -jar GW-1.0-SNAPSHOT.jar


Testing profile when rate=1000(in app.properties):  
avg = 1633306.9, 0 = 260000, 50 = 1534000, 95 = 2282000, 99 = 3789000, 100 = 49550000  
avg = 1637608.8, 0 = 258000, 50 = 1540000, 95 = 2416000, 99 = 4363000, 100 = 30082000  
avg = 1755783.3, 0 = 315000, 50 = 1692000, 95 = 2526000, 99 = 3575000, 100 = 28275000  

Existing issue:  
- I've tried to run the rules checking in parallel(multi-tasking), 
however, I found if I used ExecutorService, occasionally The F message was processed early than
D message due to threading competition, which would cause issue of more messages were forwarded
to TCP connection. So the testing is based on single threaded rules checking.
