1.Correct rasPtr bit-width calculation — it should be determined by the speculative stack size, not the commit stack size parameter.
2.Clean up confusing naming between stackSize and specSize in the Ras module.
