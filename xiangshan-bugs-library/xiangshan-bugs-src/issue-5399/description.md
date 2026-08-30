When some predictor (currently only tage) is not ready for train, ftq will continuously send the same valid train to Bpu. If other predictors uses their own `io.train.fire` (i.e. `ftqTrain.valid && self.ready`), they might train multiple times on a same entry, which is bad.

We need each predictor to use the same `ftqTrain.fire` (i.e. `ftqTrain.valid && allPredictors.map(_.ready).reduce(_ || _)`) to avoid the issue.
