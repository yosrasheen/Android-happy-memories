# Android-happy-memories
This project use an emotion detection API to continuously detect emotion which is affectiva
If emotion deviates within a certain threshold (unknown),
we can gather a digest of some happy memories of the user from social media which is facebook.
To read data from facebook Opengraph is used
Phase 1: 
    Use Affectiva to measure sadness
    Gather data to get an average (threshold)
Phase 2: 
    Use Affectiva to detect sadness
    Gather happy posts from Facebook Using Open Graph API and show one of them randomly when sadness exceeds the threshold
    Use Affectiva to measure sadness again to see the difference

Phase 1:
I used Affectiva for android platform. The project detected the sadness emotion extracted by Affectiva which is represented by a range of numerical values from 0 to 1 where 1 indicates sadness. 
Based on experiment, I notice that sadness detection is not accurate. The person needs to show deep facial expression to be detected accurately
The project Capture pictures when Unlock the phone or Every minute while unlocking 
Measure sadness values and store it
After the data gathering phase, calculate the average and use it as a threshold

Phase 2:
Capture pictures when : Unlock the phone or every minute while unlocking
When the result is above the threshold, Facebook post is to be shown
Record the sadness level as S1
The Graph API is the primary way to get data out of, and put data into, Facebook's platform. 
"fields", "id,name,feed.type(photo).limit(1000){id,link,object_id,reactions.limit(1000){id,name,type}}"
Filter them based on : Type = photo and Reaction = Haha
Use affectiva to determine the sadness level after showing the facebook posts
Record the sadness level as S2
See the difference which is
D= S1 – S2
If D is positive, then sadness level is reduced. Which means sadness mood is reverted.(Goal) 
If D is negative, then sadness level is increased.
If D is zero, then sadness level is not affected.  

