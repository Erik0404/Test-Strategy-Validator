# Test Strategy Sample

**Requirement:** IF the trainSpeed is ZeroSpeed Than the NearObc OR FarObc must send StandstillBubble to the following Component(s): TrainManagementComponent via TMCPort OR PassengerInformationComponent via PICPort OR OperationScreen via screenPort.

### Conditions
- **C1**: trainSpeed is ZeroSpeed *(Source: "trainSpeed is ZeroSpeed")*

### Expectations
- **E1**: NearObc sends StandstillBubble to TrainManagementComponent via TMCPort *(Source: "NearObc must send StandstillBubble to TrainManagementComponent via TMCPort")*
- **E2**: NearObc sends StandstillBubble to PassengerInformationComponent via PICPort *(Source: "NearObc must send StandstillBubble to PassengerInformationComponent via PICPort")*
- **E3**: NearObc sends StandstillBubble to OperationScreen via screenPort *(Source: "NearObc must send StandstillBubble to OperationScreen via screenPort")*
- **E4**: FarObc sends StandstillBubble to TrainManagementComponent via TMCPort *(Source: "FarObc must send StandstillBubble to TrainManagementComponent via TMCPort")*
- **E5**: FarObc sends StandstillBubble to PassengerInformationComponent via PICPort *(Source: "FarObc must send StandstillBubble to PassengerInformationComponent via PICPort")*
- **E6**: FarObc sends StandstillBubble to OperationScreen via screenPort *(Source: "FarObc must send StandstillBubble to OperationScreen via screenPort")*

### Rules
- C1 => E1, E2, E3, E4, E5, E6

### Coverage
| C1 | Expected | Covered |
| --- | --- | --- |
| False | - | - |
| True | E1 && E2 && E3 && E4 && E5 && E6 | Test01: Step02 |
