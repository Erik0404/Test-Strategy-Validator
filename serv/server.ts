import express, { Request, Response } from 'express';
import OpenAI from 'openai';
import 'dotenv/config';

const app = express(); //Szerver peldany
app.use(express.json()); //Automatikus json deserializalo

const openai = new OpenAI({ //Objektum az AI kommunikáciora
  baseURL: 'https://openrouter.ai/api/v1',
  apiKey: process.env.OPENROUTER_API_KEY,
});

/** A metamodellben mar deklaralt pred/error nevek. Ezeket az LLM-nek TILOS ujra deklaralnia. */
function extractDeclaredRelations(metaModel: string): string[] {
  const names = new Set<string>();
  const regex = /^[ \t]*(?:pred|error|fn)[ \t]+([A-Za-z_]\w*)[ \t]*\(/gm;
  let match: RegExpExecArray | null;
  while ((match = regex.exec(metaModel)) !== null) {
    names.add(match[1]);
  }
  return [...names];
}

/** A metamodellben peldanyositott konkret csomopontok (pl. TMCPort, t1). Ezek NEM osztalyok. */
function extractNodeNames(metaModel: string): string[] {
  const names = new Set<string>();
  const factPattern = /^[A-Za-z_]\w*\(([A-Za-z_]\w*)\)\.$/;
  let insideDeclaration = false;

  for (const rawLine of metaModel.split(/\r?\n/)) {
    const line = rawLine.replace(/%.*$/, '').trim();
    if (line.length === 0) continue;

    // A pred/error/fn torzsek is tartalmaznak "Class(var)." alaku atomokat,
    // de azok valtozok, nem csomopontok - ezeket at kell ugrani.
    if (/^(?:pred|error|fn)\b/.test(line)) {
      insideDeclaration = !line.endsWith('.');
      continue;
    }
    if (insideDeclaration) {
      if (line.endsWith('.')) insideDeclaration = false;
      continue;
    }

    const match = factPattern.exec(line);
    if (match) names.add(match[1]);
  }
  return [...names];
}

/** A Refinery nyers hibauzenetebol celzott, cselekvesre fordithato javitasi tanacsot keszit. */
function buildRepairHints(lastError: string): string[] {
  const hints = new Set<string>();

  const duplicates = [...lastError.matchAll(/Duplicate name '([A-Za-z_]\w*)'/g)].map(m => m[1]);
  const ambiguous = [...lastError.matchAll(/Ambiguous reference to Relation '([A-Za-z_]\w*)'/g)].map(m => m[1]);
  const clashing = [...new Set([...duplicates, ...ambiguous])];
  if (clashing.length > 0) {
    hints.add(
      `You REDECLARED relation(s) that already exist in the metamodel: ${clashing.join(', ')}. ` +
      `Delete those declarations completely and simply CALL the existing ones in your rule bodies. ` +
      `Every new declaration you emit must start with the "gen_" prefix.`
    );
  }

  const unresolved = [...lastError.matchAll(/Couldn't resolve reference to Relation '([A-Za-z_]\w*)'/g)].map(m => m[1]);
  if (unresolved.length > 0) {
    hints.add(
      `You called ${unresolved.join(', ')} as if it were a class or predicate, but it is a concrete NODE instance. ` +
      `Nodes may only appear as ARGUMENTS, e.g. allowedBubbles(TMCPort, b) - never as TMCPort(p). ` +
      `To test "p is the TMC port" use the existing helper isTargetTmc(p) (likewise isTargetPic, isTargetOscr).`
    );
  }

  if (/missing '\)' at ';'|extraneous input '\)'|no viable alternative at input ';'/.test(lastError)) {
    hints.add(
      `You used ';' (OR) inside parentheses. Refinery does NOT support parenthesised disjunction. ` +
      `';' may only separate whole clause bodies at the top level of a declaration. ` +
      `Factor the disjunction out into its own helper predicate instead.`
    );
  }

  if (/Expected boolean expression; got node instead/.test(lastError)) {
    hints.add(
      `A clause body element was a bare variable or node instead of a relation call. ` +
      `Every literal must look like Relation(args) or !Relation(args).`
    );
  }

  if (/no viable alternative at input '='/.test(lastError)) {
    hints.add(
      `You used '='. There is no assignment or equality operator here; express everything with relation atoms ` +
      `and the existing helper predicates.`
    );
  }

  if (/no viable alternative at input '<EOF>'|mismatched input .* expecting '\.'/.test(lastError)) {
    hints.add(`A declaration was left unterminated. Every pred/error declaration must end with a '.' character.`);
  }

  const unsatScenarios = [...lastError.matchAll(/Test scenario (\S+) is UNSATISFIABLE/g)].map(m => m[1]);
  if (unsatScenarios.length > 0) {
    hints.add(
      `Test scenario(s) ${unsatScenarios.join(', ')} describe a situation that cannot exist. Check that ` +
      `(a) every object contained by the event is attached through the event's own containment reference ` +
      `with a class that reference accepts, (b) you did not create new Train/System/Platform/Port/component objects ` +
      `and did not reuse an existing node name for a new object, and (c) your own error rules do not forbid a LEGAL ` +
      `situation - a NEGATIVE scenario must still be a legal state, so if it is rejected your constraints are too strong.`
    );
  }

  if (/contained no testScenarios|has no refineryFacts/.test(lastError)) {
    hints.add(
      `Return a non-empty 'testScenarios' array. Every scenario needs non-empty 'refineryFacts' that pin the input ` +
      `situation, and the suite needs both POSITIVE and NEGATIVE scenarios.`
    );
  }

  const timedOut = [...lastError.matchAll(/Test scenario (\S+) TIMED OUT/g)].map(m => m[1]);
  if (timedOut.length > 0) {
    hints.add(
      `The solver could not complete test scenario(s) ${timedOut.join(', ')} in time. Pin the situation more ` +
      `explicitly: assert the event, attach it with trainEvent(t1, ...), and assert every object the event contains.`
    );
  }

  return [...hints];
}

app.post('/generate-validated-scenario', async (req: Request, res: Response) => { //POST kerest varunk, 
                                                                                  //async kell a tobbszalu futashoz
  try {
    const { requirement, metaModel, lastError } = req.body; //a req.body amit a Java app kuld, ezt bontjuk ki az AI-nak

    const reservedNames = extractDeclaredRelations(metaModel);
    const nodeNames = extractNodeNames(metaModel);

    let systemMessage = `You are a Test Engineer and a formal verification expert for an Autonomous Train system.
Your task is threefold:
1. Generate ONLY THE MISSING 'pred' and 'error' constraints for the Refinery model.
2. Create a clean, ATOMIC Test Strategy from the requirement.
3. Design the TEST SUITE as concrete Refinery test scenarios (see RULE 3).

=====================================================================
RULE 0 - NEVER REDECLARE ANYTHING (this is the #1 cause of rejection)
=====================================================================
The metamodel ALREADY declares these relations:
${reservedNames.join(', ')}
You MUST NOT declare any of them again - re-emitting them causes "Duplicate name" and
"Ambiguous reference" errors and your whole answer is thrown away. Call them, do not redefine them.
Every NEW declaration you introduce MUST carry the "gen_" prefix
(e.g. 'pred gen_hasStandstillOnTmc(Port p) <-> ...'), which guarantees the name is free.
Output ONLY your new declarations - never copy any part of the metamodel back.

=====================================================================
RULE 1 - NODES ARE NOT CLASSES
=====================================================================
These are concrete NODE instances, not classes and not predicates:
${nodeNames.join(', ')}
They may appear ONLY as arguments:  allowedBubbles(TMCPort, b)   [correct]
Never as a relation call:           TMCPort(p)                   [WRONG -> "Couldn't resolve reference"]
To identify a port use the existing helpers isTargetTmc(p), isTargetPic(p), isTargetOscr(p), isTargetPort(p).

=====================================================================
RULE 2 - REFINERY DATALOG SYNTAX
=====================================================================
* AND is ',' - OR is ';' - NOT is '!'.
* ';' may ONLY separate top-level clause bodies. Parenthesised disjunction is FORBIDDEN:
      WRONG:   pred p(Port x) <-> allowedBubbles(x, b), (ArrivalEvent(b); StandstillBubble(b)).
      CORRECT: pred gen_relevantBubble(Bubble b) <-> ArrivalEvent(b); StandstillBubble(b).
               pred gen_p(Port x) <-> allowedBubbles(x, b), gen_relevantBubble(b).
* Every body element must be a relation call or its negation: Relation(args) or !Relation(args).
  A bare variable or node is not a boolean -> "Expected boolean expression; got node instead".
* Head parameters must be TYPED:  pred gen_x(Train t, Port p) <-> ... .
* Variables are implicitly existentially quantified - there is NO 'exists' keyword.
* There is no '=' operator.
* '_' is the anonymous variable.
* Every declaration ends with a '.'.
* DO NOT invent new classes or events. Use ONLY elements explicitly defined in the metamodel.
* Keep the output MINIMAL: one or two helper preds plus the error rules the requirement really needs.
* ANTI-CHEAT: You MUST write actual 'error' (and optionally 'pred') Datalog code. You CANNOT return an empty string or just a comment.

Shape to follow:

pred gen_<helperName>(<Class> <var>) <->
    <ExistingRelation>(<var>, <other>),
    <ExistingClass>(<other>).

error gen_<violationName>(<Class> <var>) <->
    <condition atoms>,
    !gen_<helperName>(<var>).

=====================================================================
RULE 3 - TEST SCENARIOS (this IS the test suite that gets measured)
=====================================================================
Constraints alone are not a test suite: the solver would return arbitrary models that all fall
into the same branch, so the negative / ELSE side of the requirement would never be tested.
Therefore you MUST design 'testScenarios'. A scenario pins ONLY the INPUT situation with a few
concrete facts; Refinery completes the model from the metamodel and your constraints, derives the
outputs, and REJECTS the scenario if the situation is impossible.

Coverage rules for the suite:
* POSITIVE: for every rule at least one scenario in which all of its conditions hold.
* NEGATIVE: for every condition at least one scenario in which it does NOT hold, while the situation
  is still a LEGAL state of the system (e.g. a different event, or a different value of the same choice).
* ALTERNATIVES: if the requirement lists alternatives (several causes, locations, values),
  every alternative must appear in at least one scenario.
* Make the scenarios differ from each other; prefer a few diverse scenarios over many similar ones.

Fact syntax - facts only, no pred / error / scope / default:
* Class assertion:     ClassName(newNode).
* Relation assertion:  relationName(node, node).
* New object names: lowercase letters and digits (ev1, cause1, win1), never a RULE 1 node name.
* The train is always t1. Its single event is attached with trainEvent(t1, <eventNode>).
  Objects contained by the event are attached through the event's own containment references
  exactly as declared in the metamodel.
* Assert INPUTS only (the event and what it contains). Do NOT assert bubbles or allowedBubbles:
  deriving the outputs is exactly what the model has to show.
* Never create additional Train, System, Platform, Port, component or on-board computer objects.

CRITICAL TEST STRATEGY RULES:
1. STRICT SEPARATION: 'conditions' are ONLY inputs/states (e.g., trainSpeed is ZeroSpeed). 'expectations' are ONLY outputs/actions (e.g., sending a bubble). DO NOT put actions into conditions!
2. ATOMIC EXPECTATIONS: Split compound expectations (e.g., "send to TMC OR PIC OR Screen") into E1: "send to TMC", E2: "send to PIC", E3: "send to Screen".
3. RULE LOGIC SYNTAX: The 'logic' field can ONLY contain condition IDs (e.g., C1, C2) and logical operators (&&, ||, !).
Your response MUST be strictly in JSON format:
      {
        "missingRefineryCode": "// Your syntax-perfect Datalog code here, every new name prefixed with gen_",
        "strategyData": {
          "requirement_text": "COPY THE EXACT ORIGINAL REQUIREMENT TEXT HERE",
          "conditions": [
            {"id": "C1", "description": "<Atomic condition 1>", "source_text": "<Source 1>"},
            {"id": "C2", "description": "<Atomic condition 2>", "source_text": "<Source 2>"}
          ],
          "expectations": [
            {"id": "E1", "description": "<Atomic expectation 1>", "source_text": "<Source 1>"},
            {"id": "E2", "description": "<Atomic expectation 2>", "source_text": "<Source 2>"}
          ],
          "rules": [
            {"logic": "C1 || C2", "result_ids": ["E1", "E2"]}
          ]
        },
        "testScenarios": [
          {"id": "TS1", "covers": ["C1", "C2"], "description": "<positive case>", "refineryFacts": "<EventClass>(ev1).\\ntrainEvent(t1, ev1)."},
          {"id": "TS2", "covers": ["!C1"], "description": "<negative case>", "refineryFacts": "<OtherEventClass>(ev1).\\ntrainEvent(t1, ev1)."}
        ]
      }
      Use this metamodel to verify relationships: ${metaModel}`;

    if (lastError) { //Ha a Refinery hibat talal, akkor a kovetkezo hivasba visszakuldi azt az AI-nak (hozzafuzi)
        const hints = buildRepairHints(lastError);
        systemMessage += `\n\n=====================================================================
REPAIR ROUND - YOUR PREVIOUS ANSWER WAS REJECTED BY REFINERY
=====================================================================
Raw validator output:
${lastError}
`;
        if (hints.length > 0) {
            systemMessage += `\nHow to fix it:\n${hints.map((h, i) => `${i + 1}. ${h}`).join('\n')}\n`;
        }
        systemMessage += `\nRewrite the WHOLE answer from scratch: keep 'missingRefineryCode' as small as possible,
prefix every new name with "gen_", reuse the already existing relations listed in RULE 0, and return
a complete 'testScenarios' array that follows RULE 3.`;
    }

    //AI hivasa
    const completion = await openai.chat.completions.create({ //await: varunk az AI valaszara
      model: "openai/gpt-4o",
      messages: [
        { role: "system", content: systemMessage },
        { role: "user", content: requirement }
      ],
      response_format: { type: "json_object" } //AI respones must be in json
    });

    const aiData = JSON.parse(completion.choices[0].message.content || '{}');
    aiData.usage = completion.usage;
    res.json(aiData);

  } catch (error: any) {
    res.status(500).json({ error: error.message });
  }
});

app.listen(3000, () => console.log("AI Bridge fut a 3000-es porton")); //Open port, and start the server