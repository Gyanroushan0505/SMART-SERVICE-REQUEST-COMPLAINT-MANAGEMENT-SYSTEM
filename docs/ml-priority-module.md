# Weka Priority Classification Module

## What it does
The project now trains two independent Weka text classifiers:
1. `complaints.arff` -> category prediction.
2. `complaints_priority.arff` -> High / Medium / Low priority prediction.

Both use `StringToWordVector` and `NaiveBayes`.

## Selected priority vs predicted priority
The student continues selecting a priority in the complaint form. The server saves that value to `complaints.priority`. Weka independently predicts priority from the description and the server saves it to `complaints.predicted_priority`. Neither value overwrites the other. The admin dashboard can compare both.

## Files
- `src/main/java/com/smartcomplaint/service/PredictionService.java`
- `src/main/resources/complaints.arff`
- `src/main/resources/complaints_priority.arff`
- `src/main/java/com/smartcomplaint/servlet/ApiServlet.java` (already calls `PredictionService` and stores selected/predicted fields)
- `src/main/webapp/app.js` (already displays the prediction after submission and in the admin table)

## Build and deploy
From the folder containing `pom.xml`:

```bash
mvn clean package
```

Deploy `target/smart-complaint.war` to Tomcat's `webapps` folder, replacing the old deployed application only after stopping Tomcat.

## Academic limitation
The priority ARFF file contains illustrative labelled examples to demonstrate the ML pipeline. It is not enough to claim high real-world accuracy. For evaluation, expand it with many real, anonymized and staff-labelled complaints, split data into train/test sets, and calculate precision, recall, F1-score and a confusion matrix.
