# Baboon Coalition Model: System Design

## Purpose
This document explains how the model works at runtime, what entities it simulates, and which mechanisms drive coalition-gene dynamics in male baboons.

## Model Goal
The simulation is an agent-based model (ABM) of baboon social and reproductive dynamics, focused on:
- coalition formation among post-prime/senescent males,
- competitive access to fertile females,
- inheritance and mutation of a male coalition gene,
- how these processes change coalition-gene frequency over time.

## Runtime Architecture
The code is organized around four primary classes:
- `Environment`: simulation state, global parameters, world/grid setup, group initialization.
- `Group`: local social arena where dominance hierarchies, coalition challenges, fission, and dispersal are resolved.
- `Baboon`: individual agent logic for aging, maturation, reproduction, death, migration, and genotype inheritance.
- `Experimenter`: observer/data collection layer for population-level coalition-gene metrics.

## Time Model
- One simulation step is one day.
- `Baboon.step()` advances individual state each day.
- `Group.step()` updates group-level processes each day.
- `Environment.step()` provides periodic debug summaries.

## Core Entities
### Baboon Agent
Each baboon tracks:
- sex, age, max lifespan, juvenile/adult status,
- group membership and location,
- reproductive state (females),
- fighting ability, dominance rank, coalition genotype (males),
- lineage context (`matrilineID`, mother pointer for juvenile following in fission).

### Group Agent
Each group tracks:
- member bag,
- local fertile females and consort males,
- coalition pairings and challenge resolution,
- group-level demographic events (dispersal/fission).

### Environment
Environment controls:
- initial population and group seeding,
- spatial placement of groups in a sparse 2D space,
- parameterized limits (min/max group size, max population, migration mortality, mutation rate),
- scheduling of all steppable entities.

## Key Model Features
### 1) Dominance Hierarchy from Age-Linked Fighting Ability
- Adult male fighting ability is calculated with a logistic decline by age.
- Groups sort adult males by fighting ability each step.
- Dominance rank (1 = highest) is reassigned from this ordering.

### 2) Fertility and Consortship Game
- Adult females are fertile during cycle days 27-33 (33-day cycle).
- Fertile females are prioritized by closeness to day 30.
- Top-ranked available males are assigned initial consortships.
- Mating is recorded daily while consortship exists.

### 3) Coalition Challenge Mechanism
- Eligible coalition males are non-consort adult males with the coalition gene in post-prime or senescent stages.
- Eligible males are randomly paired into coalitions.
- Coalitions challenge current consorts for fertile females.
- Win/loss is currently stochastic (50/50 baseline), with injury/death risk applied to participants.

### 4) Reproduction and Paternity
- At cycle end, pregnancy occurs probabilistically if mating history exists.
- Father is selected by weighted random draw from recorded mating frequencies.
- Gestation + nursing + postpartum delay is represented as a single countdown (`gestationRemaining`).
- Birth creates a new juvenile agent in the mother’s group.

### 5) Coalition Gene Inheritance and Mutation
- Only male offspring can carry the coalition gene.
- Male genotype is inherited from father with configurable mutation probability.
- Mutation flips inherited state (carrier <-> non-carrier).

### 6) Life History and Demography
- Juvenile survival check at age 365 days (infant mortality process).
- Maturation thresholds: females ~6 years, males ~7 years.
- Mature males disperse from natal group; some die during migration using a configurable mortality rate.
- Groups below minimum size disperse into nearby groups.
- Groups above maximum size split (fission) into parent/daughter groups with members partitioned and juveniles preferentially following mothers.

## Data and Outputs
`Experimenter` tracks and exposes:
- number of adult males carrying the coalition gene,
- percentage of adult males carrying the coalition gene,
- cumulative reproductive success by male genotype and life stage (tracked; bar chart hook exists but is currently commented out).

Console debugging (periodic) reports:
- population totals,
- juvenile/adult and sex counts,
- coalition-gene counts/frequency,
- average fighting ability and hierarchy diagnostics.

## Initialization Flow
At simulation start:
1. Environment initializes sparse space.
2. Groups are created with variable sizes under configured bounds.
3. Baboons are instantiated with age/sex assignments and juvenile cap.
4. Adult female matrilines are seeded; juveniles receive matriline IDs.
5. Initial male coalition-gene status is assigned by configured starting frequency.
6. Groups and baboons are scheduled for repeating execution.
7. Observer (`Experimenter`) is initialized for chart/data collection.

## Main Adjustable Parameters
Important tunable parameters in `Environment` include:
- `n`, `groups`, `minGroupSize`, `maxGroupSize`, `maxPopulation`,
- `initialCoalitionFrequency`, `mutationRate`, `migrationMortalityRate`.

Changing these parameters will alter demographic structure, selection pressure, and coalition-gene trajectory.

## Known Simplifications
- Coalition challenge outcome is currently a simple probabilistic rule.
- Coalition pair formation is random among eligible males.
- Group movement/fission mechanics are simplified to maintain stable spatial mixing and avoid clustering artifacts.

These are intentional abstractions and can be extended in future versions.

## How to Run
The GUI entry point is:
- `src/baboons/GUI.java` (`main` method)

It initializes:
- two time-series charts for coalition-gene count and percentage,
- simulation classes (`Environment`, `Experimenter`, `GUI`),
- sparse-space visualization for groups.

