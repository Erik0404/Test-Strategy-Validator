# Test Strategy Sample

**Requirement:** IF the trainSpeed is ZeroSpeed
Than the Obc shall send StandstillBubble to the following Component(s):
TrainManagementComponent via TMCPort OR PassengerInformationComponent via PICPort OR OperationScreen via screenPort.

### Conditions
- **C1**: trainSpeed is ZeroSpeed *(Source: "trainSpeed is ZeroSpeed")*

### Expectations
- **E1**: send StandstillBubble to TrainManagementComponent via TMCPort *(Source: "TrainManagementComponent via TMCPort")*
- **E2**: send StandstillBubble to PassengerInformationComponent via PICPort *(Source: "PassengerInformationComponent via PICPort")*
- **E3**: send StandstillBubble to OperationScreen via screenPort *(Source: "OperationScreen via screenPort")*

### Rules
- C1 => E1, E2, E3

### Coverage
| C1 | Expected | Covered |
| --- | --- | --- |
| False | - | - |
| True | E1 && E2 && E3 | Test01: Step02 |
