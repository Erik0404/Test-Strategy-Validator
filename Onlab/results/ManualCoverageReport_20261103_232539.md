# Manual (golden) test suite - object coverage

Every scenario below was solved by Refinery against the metamodel, therefore the manual reference suite is formally validated as well.

## REQ_01

> IF the trainSpeed is ZeroSpeed THEN the NearObc OR FarObc must send StandstillBubble to the following Component(s): TrainManagementComponent via TMCPort OR PassengerInformationComponent via PICPort OR OperationScreen via screenPort.

**Manual object coverage: 7 / 7 (100.0%)** over 13 instance model(s) generated from 2 hand-written scenario(s).

| Item | Pattern | Trace | Covered | Witnessing models |
| --- | --- | --- | --- | --- |
| I01_C1_ZeroSpeed | `ZeroSpeed(x)` | C1 | yes | M1, M2, M3, M4, M5, M6, M7, M8, M9, M10 |
| I01_C1N_NotZeroSpeed | `NOT ZeroSpeed(x)` | C1 | yes | M11, M12, M13 |
| I01_E_StandstillExists | `StandstillBubble(x) [when ZeroSpeed]` | E* | yes | M1, M2, M3, M4, M5, M6, M7, M8, M9, M10 |
| I01_E_TMC | `allowedBubbles(TMCPort, y:StandstillBubble)` | E1 | yes | M1, M2, M3, M4, M5, M6, M7, M8, M9, M10 |
| I01_E_PIC | `allowedBubbles(PICPort, y:StandstillBubble)` | E2 | yes | M1, M2, M3, M4, M5, M6, M7, M8, M9, M10 |
| I01_E_SCR | `allowedBubbles(screenPort, y:StandstillBubble)` | E3 | yes | M1, M2, M3, M4, M5, M6, M7, M8, M9, M10 |
| I01_EN_NoStandstillTMC | `NOT allowedBubbles(TMCPort, y:StandstillBubble)` | E1 | yes | M11, M12, M13 |

## REQ_02

> IF the train event is Arrival AND the doors are DoorsOpen THEN the PassengerInformationComponent must receive an ArrivalEvent via PICPort.

**Manual object coverage: 7 / 7 (100.0%)** over 13 instance model(s) generated from 2 hand-written scenario(s).

| Item | Pattern | Trace | Covered | Witnessing models |
| --- | --- | --- | --- | --- |
| I02_C1_Arrival | `Arrival(x)` | C1 | yes | M1, M2, M3, M4, M5, M6, M7, M8, M9, M10 |
| I02_C1N_NotArrival | `NOT Arrival(x)` | C1 | yes | M11, M12, M13 |
| I02_C2_DoorsOpen | `DoorsOpen(x)` | C2 | yes | M1, M2, M3, M4, M5, M6, M7, M8, M9, M10 |
| I02_C2N_DoorsNotOpen | `NOT DoorsOpen(x)` | C2 | yes | M11, M12, M13 |
| I02_E_ArrivalEventExists | `ArrivalEvent(x)` | E1 | yes | M1, M2, M3, M4, M5, M6, M7, M8, M9, M10 |
| I02_E_PIC_Arrival | `allowedBubbles(PICPort, y:ArrivalEvent)` | E1 | yes | M1, M2, M3, M4, M5, M6, M7, M8, M9, M10 |
| I02_EN_PIC_NoArrival | `NOT allowedBubbles(PICPort, y:ArrivalEvent)` | E1 | yes | M11, M12, M13 |

## REQ_03

> IF the train event is Departure AND the speed is MovingSpeed THEN the TrainManagementComponent must receive a DepartureEvent bubble via TMCPort.

**Manual object coverage: 7 / 7 (100.0%)** over 13 instance model(s) generated from 2 hand-written scenario(s).

| Item | Pattern | Trace | Covered | Witnessing models |
| --- | --- | --- | --- | --- |
| I03_C1_Departure | `Departure(x)` | C1 | yes | M11, M12, M13 |
| I03_C1N_NotDeparture | `NOT Departure(x)` | C1 | yes | M1, M2, M3, M4, M5, M6, M7, M8, M9, M10 |
| I03_C2_MovingSpeed | `MovingSpeed(x)` | C2 | yes | M11, M12, M13 |
| I03_C2N_NotMovingSpeed | `NOT MovingSpeed(x)` | C2 | yes | M1, M2, M3, M4, M5, M6, M7, M8, M9, M10 |
| I03_E_DepartureEventExists | `DepartureEvent(x)` | E1 | yes | M11, M12, M13 |
| I03_E_TMC_Departure | `allowedBubbles(TMCPort, y:DepartureEvent)` | E1 | yes | M11, M12, M13 |
| I03_EN_TMC_NoDeparture | `NOT allowedBubbles(TMCPort, y:DepartureEvent)` | E1 | yes | M1, M2, M3, M4, M5, M6, M7, M8, M9, M10 |

## REQ_04

> IF the train is in an Arrival event, THEN both the TrainManagementComponent via TMCPort AND the OperationScreen via screenPort must receive an ArrivalEvent, AND NO DepartureEvent is allowed in these ports.

**Manual object coverage: 7 / 7 (100.0%)** over 13 instance model(s) generated from 2 hand-written scenario(s).

| Item | Pattern | Trace | Covered | Witnessing models |
| --- | --- | --- | --- | --- |
| I04_C1_Arrival | `Arrival(x)` | C1 | yes | M1, M2, M3, M4, M5, M6, M7, M8, M9, M10 |
| I04_C1N_NotArrival | `NOT Arrival(x)` | C1 | yes | M11, M12, M13 |
| I04_E_ArrivalEventExists | `ArrivalEvent(x)` | E1 | yes | M1, M2, M3, M4, M5, M6, M7, M8, M9, M10 |
| I04_E_TMC_Arrival | `allowedBubbles(TMCPort, y:ArrivalEvent)` | E1 | yes | M1, M2, M3, M4, M5, M6, M7, M8, M9, M10 |
| I04_E_SCR_Arrival | `allowedBubbles(screenPort, y:ArrivalEvent)` | E2 | yes | M1, M2, M3, M4, M5, M6, M7, M8, M9, M10 |
| I04_E_TMC_NoDeparture | `NOT allowedBubbles(TMCPort, y:DepartureEvent) [when Arrival]` | E3 | yes | M1, M2, M3, M4, M5, M6, M7, M8, M9, M10 |
| I04_E_SCR_NoDeparture | `NOT allowedBubbles(screenPort, y:DepartureEvent) [when Arrival]` | E3 | yes | M1, M2, M3, M4, M5, M6, M7, M8, M9, M10 |

## REQ_05

> IF the train event is Departure AND the doors are DoorsClosed THEN all target ports (TrainManagementComponent, PassengerInformationComponent, and OperationScreen) must receive a DepartureEvent, and simultaneously ensure that neither ArrivalEvent nor StandstillBubble are generated.

**Manual object coverage: 10 / 10 (100.0%)** over 13 instance model(s) generated from 2 hand-written scenario(s).

| Item | Pattern | Trace | Covered | Witnessing models |
| --- | --- | --- | --- | --- |
| I05_C1_Departure | `Departure(x)` | C1 | yes | M11, M12, M13 |
| I05_C1N_NotDeparture | `NOT Departure(x)` | C1 | yes | M1, M2, M3, M4, M5, M6, M7, M8, M9, M10 |
| I05_C2_DoorsClosed | `DoorsClosed(x)` | C2 | yes | M11, M12, M13 |
| I05_C2N_NotDoorsClosed | `NOT DoorsClosed(x)` | C2 | yes | M1, M2, M3, M4, M5, M6, M7, M8, M9, M10 |
| I05_E_DepartureEventExists | `DepartureEvent(x)` | E* | yes | M11, M12, M13 |
| I05_E_TMC_Dep | `allowedBubbles(TMCPort, y:DepartureEvent)` | E1 | yes | M11, M12, M13 |
| I05_E_PIC_Dep | `allowedBubbles(PICPort, y:DepartureEvent)` | E2 | yes | M11, M12, M13 |
| I05_E_SCR_Dep | `allowedBubbles(screenPort, y:DepartureEvent)` | E3 | yes | M11, M12, M13 |
| I05_E_NoArrivalEvent | `NOT ArrivalEvent(x) [when Departure]` | E4 | yes | M11, M12, M13 |
| I05_E_NoStandstill | `NOT StandstillBubble(x) [when Departure]` | E5 | yes | M11, M12, M13 |

## REQ_06

> IF the train event is EmergencyStop, caused either by ObstacleDetected OR by PassengerAlarm, THEN the NearObc AND FarObc must send an EmergencyBubble to the TrainManagementComponent via TMCPort, the PassengerInformationComponent via PICPort, the OperationScreen via screenPort AND to both Wayside units via WCU1Port AND WCU2Port. IF the EmergencyStop happens WithinStoppingWindow THEN the doors must be DoorsOpen AND an EvacuationBubble must be sent to the PassengerInformationComponent via PICPort AND to the OperationScreen via screenPort; IF it happens OutsideStoppingWindow THEN the doors must remain DoorsClosed AND NO EvacuationBubble is allowed. During an EmergencyStop NO ArrivalEvent, DepartureEvent OR StandstillBubble is allowed.

**Manual object coverage: 22 / 22 (100.0%)** over 23 instance model(s) generated from 3 hand-written scenario(s).

| Item | Pattern | Trace | Covered | Witnessing models |
| --- | --- | --- | --- | --- |
| I06_C1_Emergency | `EmergencyStop(x)` | C1 | yes | M11, M12, M13, M14, M15, M16, M17, M18, M19, M20, M21, M22, M23 |
| I06_C1N_NoEmergency | `NOT EmergencyStop(x)` | C1 | yes | M1, M2, M3, M4, M5, M6, M7, M8, M9, M10 |
| I06_C2_CauseObstacle | `emCause(x:EmergencyStop, y:ObstacleDetected)` | C2 | yes | M11, M12, M13, M14, M15, M16, M17, M18, M19, M20 |
| I06_C3_CausePassengerAlarm | `emCause(x:EmergencyStop, y:PassengerAlarm)` | C3 | yes | M21, M22, M23 |
| I06_C4_WithinWindow | `emWindow(x:EmergencyStop, y:WithinStoppingWindow)` | C4 | yes | M11, M12, M13, M14, M15, M16, M17, M18, M19, M20 |
| I06_C4N_OutsideWindow | `emWindow(x:EmergencyStop, y:OutsideStoppingWindow)` | C4 | yes | M21, M22, M23 |
| I06_E_EmergencyBubbleExists | `EmergencyBubble(x) [when EmergencyStop]` | E1-E5 | yes | M11, M12, M13, M14, M15, M16, M17, M18, M19, M20, M21, M22, M23 |
| I06_E_TMC_Emergency | `allowedBubbles(TMCPort, y:EmergencyBubble)` | E1 | yes | M11, M12, M13, M14, M15, M16, M17, M18, M19, M20, M21, M22, M23 |
| I06_E_PIC_Emergency | `allowedBubbles(PICPort, y:EmergencyBubble)` | E2 | yes | M11, M12, M13, M14, M15, M16, M17, M18, M19, M20, M21, M22, M23 |
| I06_E_SCR_Emergency | `allowedBubbles(screenPort, y:EmergencyBubble)` | E3 | yes | M11, M12, M13, M14, M15, M16, M17, M18, M19, M20, M21, M22, M23 |
| I06_E_WCU1_Emergency | `allowedBubbles(WCU1Port, y:EmergencyBubble)` | E4 | yes | M11, M12, M13, M14, M15, M16, M17, M18, M19, M20, M21, M22, M23 |
| I06_E_WCU2_Emergency | `allowedBubbles(WCU2Port, y:EmergencyBubble)` | E5 | yes | M11, M12, M13, M14, M15, M16, M17, M18, M19, M20, M21, M22, M23 |
| I06_EN_TMC_NoEmergency | `NOT allowedBubbles(TMCPort, y:EmergencyBubble)` | E1 | yes | M1, M2, M3, M4, M5, M6, M7, M8, M9, M10 |
| I06_E_DoorsOpenAtPlatform | `emDoors(x:EmergencyStop, y:DoorsOpen)` | E6 | yes | M11, M12, M13, M14, M15, M16, M17, M18, M19, M20 |
| I06_E_DoorsClosedBetween | `emDoors(x:EmergencyStop, y:DoorsClosed)` | E9 | yes | M21, M22, M23 |
| I06_E_EvacuationExists | `EvacuationBubble(x) [when EmergencyStop]` | E7-E8 | yes | M11, M12, M13, M14, M15, M16, M17, M18, M19, M20 |
| I06_E_PIC_Evacuation | `allowedBubbles(PICPort, y:EvacuationBubble)` | E7 | yes | M11, M12, M13, M14, M15, M16, M17, M18, M19, M20 |
| I06_E_SCR_Evacuation | `allowedBubbles(screenPort, y:EvacuationBubble)` | E8 | yes | M11, M12, M13, M14, M15, M16, M17, M18, M19, M20 |
| I06_EN_NoEvacuation | `NOT EvacuationBubble(x) [when EmergencyStop]` | E10 | yes | M21, M22, M23 |
| I06_EN_NoArrivalEvent | `NOT ArrivalEvent(x) [when EmergencyStop]` | E11 | yes | M11, M12, M13, M14, M15, M16, M17, M18, M19, M20, M21, M22, M23 |
| I06_EN_NoDepartureEvent | `NOT DepartureEvent(x) [when EmergencyStop]` | E12 | yes | M11, M12, M13, M14, M15, M16, M17, M18, M19, M20, M21, M22, M23 |
| I06_EN_NoStandstill | `NOT StandstillBubble(x) [when EmergencyStop]` | E13 | yes | M11, M12, M13, M14, M15, M16, M17, M18, M19, M20, M21, M22, M23 |

