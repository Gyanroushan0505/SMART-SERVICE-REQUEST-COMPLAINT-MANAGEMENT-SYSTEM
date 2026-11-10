# Smart Complaint Management System - Online Hosting Guide

## Is package mein kya add hua hai
- `Dockerfile`: Maven WAR ko Java 17 + Tomcat 10.1 par run karta hai.
- `render.yaml`: Render par Docker web service ka blueprint.
- `src/main/resources/schema-cloud.sql`: Cloud MySQL-compatible database mein tables banane ke liye.
- `DB.java`: database credentials hard-code nahi karta; environment variables use karta hai.

## Zaroori security step
Purane `DB.java` mein database password source code ke andar tha. Use package se hata diya gaya hai. Agar ye password kisi real database par use hua hai, hosting se pehle database provider par us password ko change/rotate karein. Naya password GitHub, ZIP, JavaScript ya kisi source file mein mat likhein.

## Deployment ka overview
1. ZIP extract karke `smart-complaint` folder ke andar ke files GitHub repository ke root mein push karein. Is folder mein `pom.xml`, `src`, `Dockerfile`, `render.yaml` aur `.dockerignore` honge.
2. Ek MySQL-compatible cloud database banayein. Database ka naam `smart_complaint` rakhein. Provider se hostname, port, database username, password aur SSL connection details lein.
3. Cloud database console mein `src/main/resources/schema-cloud.sql` execute karein. Existing data migrate karna ho to local database ka backup/export alag se import karein; schema file purane users/complaints ko copy nahi karti.
4. Render account mein GitHub repository connect karein aur `render.yaml` blueprint deploy karein.
5. Render service ke Environment section mein ye values set karein:
   - `DB_URL`: `jdbc:mysql://HOST:PORT/smart_complaint?sslMode=REQUIRED&serverTimezone=UTC` (HOST/PORT provider ke actual values se replace karein; provider ke SSL instructions ko priority dein.)
   - `DB_USER`: cloud database ka username
   - `DB_PASSWORD`: cloud database ka password
6. Deploy complete hone ke baad Render ka `https://....onrender.com` URL open karein. App root par Admin Login khulega. Student login `html.html` aur Admin login `admin-login.html` par hoga.

## Free plan ki limitations
Render Free web services idle hone par sleep kar sakti hain aur next request par start hone mein waqt lag sakta hai. Free plan par uptime, storage aur database ki availability guaranteed nahi hoti. Render ka free web service plan apne aap MySQL database provide nahi karta; MySQL-compatible database alag provider se lena hoga. Kisi bhi provider ke free-tier limits/availability ko sign-up ke waqt verify karein.

## Important checks before public demo
- Admin account ko secure password dein; default/temporary credentials use na karein.
- Public demo ke liye real student personal data use na karein. Dummy demo records use karein.
- Cloud database mein least-privilege user banayein; root account use na karein.
- Database backup/export rakhein. Free database tier data loss ya deletion protection guarantee nahi karta.
- Agar database provider SSL certificate verification require karta hai, uske exact JDBC SSL parameters use karein.

## Local run
Local Windows development ke liye `DB_URL`, `DB_USER`, `DB_PASSWORD` environment variables set karke Maven se WAR build karein. `DB_PASSWORD` ab hard-coded nahi hai.
