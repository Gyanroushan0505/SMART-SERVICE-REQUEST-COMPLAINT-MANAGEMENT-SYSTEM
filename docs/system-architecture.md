# High-level system architecture

```text
Student / Admin / Staff browser
             |
      HTML + CSS + JavaScript
             |  JSON over HTTP / session cookie
             v
      Java Servlet API layer
       |        |         |
       |        |         +--> Weka prediction service
       |        |                two StringToWordVector + NaiveBayes models
       |        +--> Complaint / assignment / status business rules
       v
     JDBC + MySQL
       |-- users and roles
       |-- complaints and predictions
       |-- status history
       +-- feedback

Apache Tomcat hosts the web application. Maven builds a WAR package.
```

## Main flows
1. Registration/login establishes a server-side session. Passwords are stored as salted PBKDF2 hashes.
2. A user submits a complaint. The Servlet validates it, asks Weka for both a category prediction and a priority prediction, keeps the student-selected priority separately, calculates an initial resolution-time estimate from the predicted priority, and stores all values and the initial status event in MySQL.
3. An administrator assigns a staff account and updates status/notes. Staff can update only complaints assigned to them.
4. Users view their own complaints and can leave feedback after a complaint is resolved.


## Machine-learning module
- `src/main/resources/complaints.arff` is the labelled training dataset for complaint-category classification.
- `src/main/resources/complaints_priority.arff` is the labelled training dataset for priority classification (`High`, `Medium`, `Low`).
- `PredictionService.java` trains two independent Weka `NaiveBayes` text classifiers using `StringToWordVector`.
- On complaint submission, `ApiServlet` stores the student's selected value in `complaints.priority` and the model output in `complaints.predicted_priority`. The predicted category is stored in `complaints.predicted_category`.
- The admin dashboard already displays the student's selected priority and the ML-predicted category/priority separately.

The included ARFF records are small demonstration/training samples for the academic prototype, not a production-grade dataset. For a credible evaluation, collect many real, anonymized complaints labelled by staff, keep separate training and test sets, and report precision, recall, F1-score and a confusion matrix.
