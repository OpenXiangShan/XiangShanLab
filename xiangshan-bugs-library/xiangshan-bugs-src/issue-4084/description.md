The addition used previously to calculate the `lsq` pointer results in overflow, this is because, the bit width of `numLsElem` is 5 and multiple uop accumulations result in data overflow.

---

Theoretically this would have been a problem in previous versions as well, but for some reason the bug didn't occur in previous versions until `newDispatch`.
