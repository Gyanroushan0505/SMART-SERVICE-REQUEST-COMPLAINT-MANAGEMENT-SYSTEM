package com.smartcomplaint.service;

import weka.classifiers.meta.FilteredClassifier;
import weka.classifiers.bayes.NaiveBayes;
import weka.core.*;
import weka.filters.unsupervised.attribute.StringToWordVector;

import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Weka-based complaint prediction service.
 * Trains two independent text classifiers:
 * 1) complaint category
 * 2) complaint priority (High / Medium / Low)
 */
public class PredictionService {

    private static final PredictionService INSTANCE = new PredictionService();

    private FilteredClassifier categoryModel;
    private FilteredClassifier priorityModel;
    private Instances categoryHeader;
    private Instances priorityHeader;

    public static PredictionService get() {
        return INSTANCE;
    }

    private PredictionService() {
        try {
            categoryModel = trainModel("complaints.arff");
            priorityModel = trainModel("complaints_priority.arff");
            categoryHeader = loadHeader("complaints.arff");
            priorityHeader = loadHeader("complaints_priority.arff");
        } catch (Exception e) {
            throw new IllegalStateException("Could not train Weka complaint models", e);
        }
    }

    private Instances loadData(String resourceName) throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new IllegalStateException(resourceName + " missing from src/main/resources");
            }
            String raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Instances data = new Instances(new StringReader(raw));
            data.setClassIndex(data.numAttributes() - 1);
            return data;
        }
    }

    private Instances loadHeader(String resourceName) throws Exception {
        return new Instances(loadData(resourceName), 0);
    }

    private FilteredClassifier trainModel(String resourceName) throws Exception {
        Instances data = loadData(resourceName);

        StringToWordVector filter = new StringToWordVector();
        filter.setAttributeIndices("first");
        filter.setLowerCaseTokens(true);
        filter.setWordsToKeep(1000);

        FilteredClassifier classifier = new FilteredClassifier();
        classifier.setFilter(filter);
        classifier.setClassifier(new NaiveBayes());
        classifier.buildClassifier(data);
        return classifier;
    }

    private String classify(FilteredClassifier model, Instances header, String description) throws Exception {
        Instances one = new Instances(header, 0);
        DenseInstance instance = new DenseInstance(header.numAttributes());
        instance.setDataset(one);
        Attribute textAttribute = one.attribute(0);
        instance.setValue(textAttribute, textAttribute.addStringValue(description));
        instance.setMissing(one.classIndex());
        one.add(instance);

        double predictionIndex = model.classifyInstance(one.instance(0));
        return one.classAttribute().value((int) predictionIndex);
    }


    private boolean isHighRisk(String description) {
        String text = description == null ? "" : description.toLowerCase(Locale.ROOT);
        return text.matches("(?s).*\\b(gas leak|gas leakage|gas leaking|gas smell|leaking gas|gas is leaking|gas cylinder leak|cylinder is leaking|fire|flames|smoke|electric shock|electrical shock|live wire|exposed wire|exposed cable|electric wire sparking|sparking wire|sparks from|short circuit|burning smell|person trapped in lift|stuck in lift|lift trapped|ceiling collapsing|wall collapsing|roof collapsing|flooding|room flooded|water flooding|severe flooding)\\b.*");
    }

    public synchronized Map<String, Object> predict(String description) {
        Map<String, Object> result = new LinkedHashMap<>();
        String safeDescription = description == null ? "" : description.trim();

        String category = "Other";
        String priority = "Medium";
        String modelName = "Weka NaiveBayes + StringToWordVector";

        try {
            category = classify(categoryModel, categoryHeader, safeDescription);
        } catch (Exception e) {
            modelName = "Weka category model fallback; priority model result";
        }

        try {
            priority = classify(priorityModel, priorityHeader, safeDescription);
        } catch (Exception e) {
            // Keep a safe fallback if the priority model cannot classify the text.
            priority = "Medium";
            modelName = "Weka with safety override";
        }

        // Safety-critical complaints must never be downgraded by a statistical
        // text model merely because a particular phrase was rare in training data.
        // Weka still predicts ordinary cases; this is a transparent emergency override.
        if (isHighRisk(safeDescription)) {
            priority = "High";
            modelName = "Weka NaiveBayes + emergency safety override";
        }

        int expectedDays;
        if ("High".equals(priority)) {
            expectedDays = 1;
        } else if ("Medium".equals(priority)) {
            expectedDays = 2;
        } else {
            expectedDays = 3;
        }

        result.put("category", category);
        result.put("priority", priority);
        result.put("expectedDays", expectedDays);
        result.put("model", modelName);
        result.put("categoryModel", "Weka NaiveBayes + StringToWordVector");
        result.put("priorityModel", "Weka NaiveBayes + StringToWordVector");
        return result;
    }
}
