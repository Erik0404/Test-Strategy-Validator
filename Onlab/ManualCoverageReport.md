# Manual (golden) test suite - object coverage

Every scenario below was solved by Refinery against the metamodel, therefore the manual reference suite is formally validated as well.

## REQ_01

> IF the trainSpeed is ZeroSpeed THEN the NearObc OR FarObc must send StandstillBubble to the following Component(s): TrainManagementComponent via TMCPort OR PassengerInformationComponent via PICPort OR OperationScreen via screenPort.

**Manual object coverage: 7 / 7 (100.0%)** over 20 instance model(s) generated from 2 hand-written scenario(s).

| Item | Pattern | Trace | Covered | Witnessing models |
| --- | --- | --- | --- | --- |
| I01_C1_ZeroSpeed | `ZeroSpeed(x)` | C1 | yes | M1, M2, M3, M4, M5, M6, M7, M8, M9, M10 |
| I01_C1N_NotZeroSpeed | `NOT ZeroSpeed(x)` | C1 | yes | M11, M12, M13, M14, M15, M16, M17, M18, M19, M20 |
| I01_E_StandstillExists | `StandstillBubble(x)` | E* | yes | M1, M2, M3, M4, M5, M6, M7, M8, M9, M10, M13, M15, M16, M17, M18, M19, M20 |
| I01_E_TMC | `allowedBubbles(TMCPort, y:StandstillBubble)` | E1 | yes | M1, M2, M3, M4, M5, M6, M7, M8, M9, M10 |
| I01_E_PIC | `allowedBubbles(PICPort, y:StandstillBubble)` | E2 | yes | M1, M2, M3, M4, M5, M6, M7, M8, M9, M10 |
| I01_E_SCR | `allowedBubbles(screenPort, y:StandstillBubble)` | E3 | yes | M1, M2, M3, M4, M5, M6, M7, M8, M9, M10 |
| I01_EN_NoStandstillTMC | `NOT allowedBubbles(TMCPort, y:StandstillBubble)` | E1 | yes | M11, M12, M13, M14, M15, M16, M17, M18, M19, M20 |

## REQ_02

> IF the train event is Arrival AND the doors are DoorsOpen THEN the PassengerInformationComponent must receive an ArrivalEvent via PICPort.

**Manual object coverage: 7 / 7 (100.0%)** over 20 instance model(s) generated from 2 hand-written scenario(s).

| Item | Pattern | Trace | Covered | Witnessing models |
| --- | --- | --- | --- | --- |
| I02_C1_Arrival | `Arrival(x)` | C1 | yes | M1, M2, M3, M4, M5, M6, M7, M8, M9, M10 |
| I02_C1N_NotArrival | `NOT Arrival(x)` | C1 | yes | M11, M12, M13, M14, M15, M16, M17, M18, M19, M20 |
| I02_C2_DoorsOpen | `DoorsOpen(x)` | C2 | yes | M1, M2, M3, M4, M5, M6, M7, M8, M9, M10 |
| I02_C2N_DoorsNotOpen | `NOT DoorsOpen(x)` | C2 | yes | M11, M12, M13, M14, M15, M16, M17, M18, M19, M20 |
| I02_E_ArrivalEventExists | `ArrivalEvent(x)` | E1 | yes | M1, M2, M3, M4, M5, M6, M7, M8, M9, M10 |
| I02_E_PIC_Arrival | `allowedBubbles(PICPort, y:ArrivalEvent)` | E1 | yes | M1, M2, M3, M4, M5, M6, M7, M8, M9, M10 |
| I02_EN_PIC_NoArrival | `NOT allowedBubbles(PICPort, y:ArrivalEvent)` | E1 | yes | M11, M12, M13, M14, M15, M16, M17, M18, M19, M20 |

## REQ_03

> IF the train event is Departure AND the speed is MovingSpeed THEN the TrainManagementComponent must receive a DepartureEvent bubble via TMCPort.

**Manual object coverage: 7 / 7 (100.0%)** over 20 instance model(s) generated from 2 hand-written scenario(s).

| Item | Pattern | Trace | Covered | Witnessing models |
| --- | --- | --- | --- | --- |
| I03_C1_Departure | `Departure(x)` | C1 | yes | M11, M12, M13, M14, M15, M16, M17, M18, M19, M20 |
| I03_C1N_NotDeparture | `NOT Departure(x)` | C1 | yes | M1, M2, M3, M4, M5, M6, M7, M8, M9, M10 |
| I03_C2_MovingSpeed | `MovingSpeed(x)` | C2 | yes | M11, M12, M13, M14, M15, M16, M17, M18, M19, M20 |
| I03_C2N_NotMovingSpeed | `NOT MovingSpeed(x)` | C2 | yes | M1, M2, M3, M4, M5, M6, M7, M8, M9, M10 |
| I03_E_DepartureEventExists | `DepartureEvent(x)` | E1 | yes | M11, M12, M13, M14, M15, M16, M17, M18, M19, M20 |
| I03_E_TMC_Departure | `allowedBubbles(TMCPort, y:DepartureEvent)` | E1 | yes | M11, M12, M13, M14, M15, M16, M17, M18, M19, M20 |
| I03_EN_TMC_NoDeparture | `NOT allowedBubbles(TMCPort, y:DepartureEvent)` | E1 | yes | M1, M2, M3, M4, M5, M6, M7, M8, M9, M10 |

## REQ_04

> IF the train is in an Arrival event, THEN both the TrainManagementComponent via TMCPort AND the OperationScreen via screenPort must receive an ArrivalEvent, AND NO DepartureEvent is allowed in these ports.

**Manual object coverage: 7 / 7 (100.0%)** over 20 instance model(s) generated from 2 hand-written scenario(s).

| Item | Pattern | Trace | Covered | Witnessing models |
| --- | --- | --- | --- | --- |
| I04_C1_Arrival | `Arrival(x)` | C1 | yes | M1, M2, M3, M4, M5, M6, M7, M8, M9, M10 |
| I04_C1N_NotArrival | `NOT Arrival(x)` | C1 | yes | M11, M12, M13, M14, M15, M16, M17, M18, M19, M20 |
| I04_E_ArrivalEventExists | `ArrivalEvent(x)` | E1 | yes | M1, M2, M3, M4, M5, M6, M7, M8, M9, M10 |
| I04_E_TMC_Arrival | `allowedBubbles(TMCPort, y:ArrivalEvent)` | E1 | yes | M1, M2, M3, M4, M5, M6, M7, M8, M9, M10 |
| I04_E_SCR_Arrival | `allowedBubbles(screenPort, y:ArrivalEvent)` | E2 | yes | M1, M2, M3, M4, M5, M6, M7, M8, M9, M10 |
| I04_E_TMC_NoDeparture | `NOT allowedBubbles(TMCPort, y:DepartureEvent)` | E3 | yes | M1, M2, M3, M4, M5, M6, M7, M8, M9, M10 |
| I04_E_SCR_NoDeparture | `NOT allowedBubbles(screenPort, y:DepartureEvent)` | E3 | yes | M1, M2, M3, M4, M5, M6, M7, M8, M9, M10 |

## REQ_05

> IF the train event is Departure AND the doors are DoorsClosed THEN all target ports (TrainManagementComponent, PassengerInformationComponent, and OperationScreen) must receive a DepartureEvent, and simultaneously ensure that neither ArrivalEvent nor StandstillBubble are generated.

**Manual object coverage: 10 / 10 (100.0%)** over 20 instance model(s) generated from 2 hand-written scenario(s).

| Item | Pattern | Trace | Covered | Witnessing models |
| --- | --- | --- | --- | --- |
| I05_C1_Departure | `Departure(x)` | C1 | yes | M11, M12, M13, M14, M15, M16, M17, M18, M19, M20 |
| I05_C1N_NotDeparture | `NOT Departure(x)` | C1 | yes | M1, M2, M3, M4, M5, M6, M7, M8, M9, M10 |
| I05_C2_DoorsClosed | `DoorsClosed(x)` | C2 | yes | M11, M12, M13, M14, M15, M16, M17, M18, M19, M20 |
| I05_C2N_NotDoorsClosed | `NOT DoorsClosed(x)` | C2 | yes | M1, M2, M3, M4, M5, M6, M7, M8, M9, M10 |
| I05_E_DepartureEventExists | `DepartureEvent(x)` | E* | yes | M11, M12, M13, M14, M15, M16, M17, M18, M19, M20 |
| I05_E_TMC_Dep | `allowedBubbles(TMCPort, y:DepartureEvent)` | E1 | yes | M11, M12, M13, M14, M15, M16, M17, M18, M19, M20 |
| I05_E_PIC_Dep | `allowedBubbles(PICPort, y:DepartureEvent)` | E2 | yes | M11, M12, M13, M14, M15, M16, M17, M18, M19, M20 |
| I05_E_SCR_Dep | `allowedBubbles(screenPort, y:DepartureEvent)` | E3 | yes | M11, M12, M13, M14, M15, M16, M17, M18, M19, M20 |
| I05_E_NoArrivalEvent | `NOT ArrivalEvent(x)` | E4 | yes | M11, M12, M13, M14, M15, M16, M17, M18, M19, M20 |
| I05_E_NoStandstill | `NOT StandstillBubble(x)` | E5 | yes | M11, M12, M14 |

