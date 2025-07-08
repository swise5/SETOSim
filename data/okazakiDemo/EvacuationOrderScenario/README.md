# About this folder

## Scenario settings files
- file name rule: XX-YY-ZZ-*.csv
    - XX: the evacuation order scenario assumed in the simulation (L1/L2)
    - YY: the actual flood inundation scenario (L1/L2)
    - ZZ: the percentage of voluntary evacuees (03/10)
    - *: whether the trigger for the Otogawa branch river is activated or not (N/B)
        - N: not activated
        - B: activated
### columns
- area_ID
    - Evacuation area is separated into two areas:
    - 002: Otogawa area
    - 001: Others
- time
    - the timing when the evacuation instruction status changes
- voluntary
    - percentage of voluntary evacuees
        - 0.1: 10%
        - 0.03: 3%
- dependents
    - whether dependents evacuate
        - 1: yes
        - 0: no
- others
    - whether others evacuate
        - 1: yes
        - 0: no
- floodStart
    - the timing when inundation starts

### file list
- L1-L2-00-N.csv
    - non-conservative / unsafe side scenario
    - Scenario where Level 1 (L1) is predicted and evacuation orders are issued accordingly, but Level 2 (L2) inundation actually occurs.
    - There is no trigger for voluntary evacuation, as the rainfall-based trigger is not activated under the L1 prediction.
- L2-L1-03-N.csv
    - conservative / safe side scenario
    - Scenario where Level 2 (L2) is predicted and evacuation orders are issued accordingly, but Level 1 (L1) inundation actually occurs.
    - 3% of voluntary evacuees
- L2-L1-10-N.csv
    - conservative / safe side scenario
    - Scenario where Level 2 (L2) is predicted and evacuation orders are issued accordingly, but Level 1 (L1) inundation actually occurs.
    - 10% of voluntary evacuees
- L1-L2-10-B.csv
    - non-conservative / unsafe side scenario
    - Scenario where Level 1 (L1) is predicted and evacuation orders are issued accordingly, but Level 2 (L2) inundation actually occurs.
    - 10% of voluntary evacuees
    - Additional evacuation order issued for residents near the Otogawa River.
- L2-L1-10-B.csv
    - conservative / safe side scenario
    - Scenario where Level 2 (L2) is predicted and evacuation orders are issued accordingly, but Level 1 (L1) inundation actually occurs.
    - 10% of voluntary evacuees
    - Additional evacuation order issued for residents near the Otogawa River.

## Evacuation order area
- Data name: EvacuationOrderArea.shp
- The data can be merged with XX-YY-ZZ-*.csv file by key = 'area_ID'(many-to-many matching)
- area_ID
- Evacuation area is separated into two areas:
    - 002: Otogawa area
    - 001: Others


