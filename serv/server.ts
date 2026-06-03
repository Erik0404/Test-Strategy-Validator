import express, { Request, Response } from 'express';
import OpenAI from 'openai';

const app = express(); //Szerver peldany
app.use(express.json()); //Automatikus json deserializalo

const openai = new OpenAI({ //Objektum az AI kommunikáciora
  baseURL: 'https://openrouter.ai/api/v1',
  apiKey: '***API_KEY***', //TODO: Kiszervezni kornyezeti valtozoba
});

app.post('/generate-validated-scenario', async (req: Request, res: Response) => { //POST kerest varunk, 
                                                                                  //async kell a tobbszalu futashoz
  try {
    const { requirement, metaModel, lastError } = req.body; //a req.body amit a Java app kuld, ezt bontjuk ki az AI-nak

    let systemMessage = `You are a Test Engineer and a formal verification expert for an Autonomous Train system.
Your task is twofold:
1. Generate the missing 'pred' and 'error' constraints for the Refinery model.
2. Create a clean, ATOMIC Test Strategy from the requirement.

CRITICAL REFINERY DATALOG SYNTAX RULES:
1. DO NOT invent new classes or events. Use ONLY elements explicitly defined in the metamodel.
2. Logical AND is a comma (','). Logical OR is a semicolon (';'). 
3. NO 'exists' KEYWORD! Variables are implicitly existentially quantified.
4. USE EXISTING HELPERS: The metamodel already has 'isTargetPort(p)' which handles the OR logic for TMC, PIC, and Screen. Use it instead of writing complex inline disjunctions!
5. ANTI-CHEAT: You MUST write actual 'error' and 'pred' Datalog code. You CANNOT return an empty string or just a comment.

CRITICAL TEST STRATEGY RULES:
1. STRICT SEPARATION: 'conditions' are ONLY inputs/states (e.g., trainSpeed is ZeroSpeed). 'expectations' are ONLY outputs/actions (e.g., sending a bubble). DO NOT put actions into conditions!
2. ATOMIC EXPECTATIONS: Split compound expectations (e.g., "send to TMC OR PIC OR Screen") into E1: "send to TMC", E2: "send to PIC", E3: "send to Screen".
3. RULE LOGIC SYNTAX: The 'logic' field can ONLY contain condition IDs (e.g., C1, C2) and logical operators (&&, ||, !).
Your response MUST be strictly in JSON format:
      {
        "missingRefineryCode": "// Your syntax-perfect Datalog code here",
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
        systemMessage += `\nWARNING! Your previous generated code contained errors. The Refinery dropped this: ${lastError}. 
                            Please correct the logic error!`;
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
    res.json(aiData);

  } catch (error: any) {
    res.status(500).json({ error: error.message });
  }
});

app.listen(3000, () => console.log("AI Bridge fut a 3000-es porton")); //Open port, and start the server