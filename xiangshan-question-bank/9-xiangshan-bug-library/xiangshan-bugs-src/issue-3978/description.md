* fix the access check for custom CSR and remove the illegal instruction check when accessing S-mode custom CSR from VS mode. This is because we can now use the Smstateen extension to control access to custom content at different privilege levels.

* fix the misjudgment of the U-mode custom CSR.

* fix the missing access check for the stopi CSR in AIA.
