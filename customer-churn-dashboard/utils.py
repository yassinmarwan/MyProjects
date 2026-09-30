import pandas as pd
def preprocessing(df):
    ml_df = df.copy()
    X = ml_df.drop(columns=['Churn'])
    Y = ml_df['Churn']
    X = X.drop("customerID", axis=1)
    X['TotalCharges'] = pd.to_numeric(X['TotalCharges'], errors='coerce')
    X['TotalCharges'] = X['TotalCharges'].fillna(X['TotalCharges'].median())
    X["gender"] = X["gender"].map({"Female":0, "Male":1})
    X["Partner"] = X["Partner"].map({"Yes":0, "No":1})
    X["Dependents"] = X["Dependents"].map({"Yes":0, "No":1})
    X["PhoneService"] = X["PhoneService"].map({"Yes":0, "No":1})
    X["PaperlessBilling"] = X["PaperlessBilling"].map({"Yes":0, "No":1})
    X = pd.get_dummies(X, drop_first=True)
    bool_cols = X.select_dtypes(include=['bool']).columns
    X[bool_cols] = X[bool_cols].astype(int)
    Y = Y.map({"Yes":1, "No":0})
    return X, Y