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

  return [...hints];
}

app.post('/generate-validated-scenario', async (req: Request, res: Response) => { //POST kerest varunk, 
                                                                                  //async kell a tobbszalu futashoz
  try {
    const { requirement, metaModel, lastError } = req.body; //a req.body amit a Java app kuld, ezt bontjuk ki az AI-nak

    const reservedNames = extractDeclaredRelations(metaModel);
    const nodeNames = extractNodeNames(metaModel);

    let systemMessage = `You are a Test Engineer and a formal verification expert for an Autonomous Train system.
Your task is twofold:
1. Generate ONLY THE MISSING 'pred' and 'error' constraints for the Refinery model.
2. Create a clean, ATOMIC Test Strategy from the requirement.

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
        }
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
        systemMessage += `\nRewrite the WHOLE 'missingRefineryCode' from scratch, keep it as small as possible,
prefix every new name with "gen_", and reuse the already existing relations listed in RULE 0.`;
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