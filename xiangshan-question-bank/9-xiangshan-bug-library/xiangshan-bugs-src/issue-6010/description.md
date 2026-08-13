AIA-spec:

>  If a supervisor external interrupt (SEI) is injected from M-level when there is no actual interrupt from an external interrupt controller, the injected SEI is assigned an S-level priority number of 256.

> If bit 9 for a supervisor external interrupt (SEI) is one in mideleg or mvien and in mvip, causing sip.SEIP to be one, but there is no supervisor-level interrupt from the hart’s external interrupt controller (APLIC or IMSIC), then a priority number for the SEI is not supplied by the external interrupt controller as usual. In that case, the SEI is assigned a priority number of 256.
