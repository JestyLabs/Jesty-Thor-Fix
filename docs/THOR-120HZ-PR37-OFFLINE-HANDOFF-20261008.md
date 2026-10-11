# PR #37 — comentário técnico e handoff offline, 2026-10-08

> Historical offline handoff. The owner resumed this work with the Thor attached;
> current read-only checks and integration are recorded in
> [PR #37 resumed validation](THOR-120HZ-PR37-RESUME-20261008.md).
> The offline restrictions below describe the earlier session.

**Texto preparado localmente; não publicado.** A regra offline impede consultar
todos os comentários atuais, fazer push, publicar comentário ou executar CI remoto.
Manter draft/research-only; não fazer merge/release. O corpo local guardado do PR
foi lido, mas não existe um arquivo integral dos comentários nas localizações verificadas.

## Comentário técnico pronto para o PR

A análise dos binários exatos da recolha .377 encontrou uma diferença decisiva
face ao AOSP usado na revisão anterior. O `invalid mode` inferior **não retorna
BAD_VALUE antes do HWC**: o firmware regista o mismatch físico e continua.

**PROVEN nos bytes identificados** — SurfaceFlinger Build ID
`a4e0851419d45662b0fd5cd067b585bf`, SHA-256
`44c3bed3b6fef384a29c2f5c9ed66813f70617c3832b1c63c1ea13f580c50fdd`:

- `setActiveModeInHwcIfNeeded` guarda o display de altura 1240 em SF+0x8b8 e
  chama `initiateModeChange` no ativo (`0x1b0fcc`) e no secundário (`0x1b0fec`)
  com o mesmo desired ActiveModeInfo, constraints e destino da timeline.
- A segunda chamada não passa pelo setter do secundário. O setter conserva
  a verificação fatal de identidade; não precisa de aceitar um modo foreign.
- O mismatch em `0x124248..0x12424c` leva ao log `0x124368`, contador
  `0x1243f4`, armazenamento upcoming e HWC com `(~configId)&1`
  (`0x124488..0x1244a4`). O ramo também emite o alvo FPS120/60.
- O chamador testa apenas o retorno da primeira chamada (`0x1b0ff0`); ignora
  o segundo. `updateInternalStateWithChangedMode` lê/atualiza só o default
  (`0x1af980/0x1af9c8`).

**OBSERVED**: as tabelas SF/HWC são invertidas: TOP 0=60/1=120;
BOTTOM 0=120/1=60. **INFERRED**: o objeto TOP 1 gera o erro inferior1 e é
remapeado para HWC inferior0=120; o objeto TOP 0 no rollback gera erro0 e
HWC inferior1=60. Explica os erros/contadores sem exigir stale pointer ou
uma segunda tentativa inferior válida. Não se afirma que o código esteja
“corrigido”; apenas se diagnosticou o caminho existente.

A trace original tem um anchor realtime válido: linha 16,
`76246.000855 -> 1791412922079 ms epoch`. Usando logcat 2026/+01:00,
o erro inferior 23:42:17.252 e o alvo inferior120 em 76261.174043 caem no mesmo
milissegundo. Isto é consistente com a mesma invocação; não existe token runtime
para a provar. O `parent_ts` da linha 15 não foi usado para inventar alinhamento.

Na captura 23:42 há dois constraints/Process/Submit; as scopes Qualcomm não
contêm ID físico, config ou retorno. O ELF mostra que constraints enfileira e
Process/Submit chama SDM; SetDisplayAttributes prepara estado antes do atomic
commit. O DAL tem verificação de erro e release/retire fences no commit.
As 1012 scopes AtomicCommit da trace não mostram o status nem a sinalização
das fences por CRTC. Não se promove um alvo 120 a HWC_ACCEPTED/SDM_APPLIED/
DRM_ACTIVE/PHYSICAL_CADENCE. Vsync 8333333 global não é medição do BOTTOM.

O teste 12:28 foi reaberto: política SF diretamente 60/60 ->120/120, zero
iniciações e dumps ativos60/60. Não há nova evidência de política SF intermédia
60–120. O valor desired/pending à entrega permanece desconhecido.

O `services.jar` confirma a adaptação AYN com fade inferior e seleção de
`bypass_ram` segundo refresh arredondado >=110. Logs confirmam observer e uma
tentativa de escrita bypass no rollback; não confirmam todos os writes/fade/ACK
DSI. A nomenclatura pública PASS/BYPASS e B9 00/B9 11 não estabelece, sem
datasheet/driver exato, repetição de frames ou arquitetura da RAM interna.

**A causa do tearing continua HYPOTHESIS.** Os mecanismos mais fundamentados
são divergência SF/HWC secundária com efeito no pacing/timeline, ou coordenação
PASS-RAM/DFPS/DSI entre escrita e leitura do controlador. Fences/buffer swaps
continuam uma hipótese, sem erro demonstrado. Diferença 120 lógico/60 físico
isoladamente não demonstra tearing. BOTTOM oficialmente 60 Hz mantém-se como
evidência relevante; modos 120 não provam 120 frames distintos.

O [relatório completo](THOR-120HZ-EXACT-BINARY-OFFLINE-20261008.md) contém o
inventário dos 22 ELF/Build IDs, hashes essenciais, offsets e disassembly,
timelines por display, fronteiras da cadeia, hipóteses e tabela de soluções
com viabilidade/root/risco. Não há fix global de tearing demonstrado na app;
pacing de conteúdo próprio ou eventual mitigação stock 60/60 têm alcance limitado
e exigem validação causal. Não foram alterados settings, firmware ou produção.

## Handoff verificável

- Branch: `work/thor-120hz-refresh-investigation`; base local `b33762d`.
- Commit `1c22184`: leitor ELF/mini-debug/VA e leitor de classes DEX locais,
  strings ELF com offsets/hashes e teste de não reingestão do relatório.
- Commit `c3a84ab`: timelines com relógios/identidades/namespaces explícitos,
  filtros de documentação, modelo DRM versus ótica/frames distintos e regressões.
- A documentação e este handoff seguem num commit próprio; obter o hash com
  `git log -3 --oneline`, sem precisar de contactar origin.
- Raw/proprietário: permanece fora do Git, sob os bundles `C:\Temp\thor-refresh-*`.
  Novos inventários, disassembly, smali e JSON: `C:\Temp\thor-refresh-offline-20261008`.
- Main preservada: `1d4cb4b36577019ca96b753b3b0d7ff5687d9b88`.
- Tag v1.6.0 preservada: `e4542adb9fe47cccb5bc8ee5b3cffdeeec702513`.

Validação local executada e aprovada:

| Verificação | Resultado |
|---|---|
| `scripts/test-refresh-investigation.ps1` | modelo Java, 13 testes Python, fixtures capture/strings e contrato passaram |
| `scripts/test-boot-lid.ps1` | suites host passaram; nenhuma execução ADB |
| `scripts/test-dashboard.ps1` | suites host passaram |
| `InspectFrameworkClass` com apktool local | compilação e leitura de services/framework passaram |
| Inventário 22 ELF versus manifestos device e host | 22 hashes coincidentes; zero caminhos em falta |
| Analisador nos bundles reais | 23:42: 45 eventos/1 anchor; 12:28: 20 eventos/sem anchor atrace |
| `git diff --check` e `scripts/prepublish.ps1` | passaram antes dos commits; sem dumps/binários/raw staged |

Não se executou CI remoto nem build/instalação de APK. As alterações são de
investigação e host; isso não equivale a validação física de uma correção.

## Continuação segura

1. Em sessão com acesso autorizado, localizar o driver/sysfs exato da .377 e
   datasheet CH13726A; seguir B9/PASS/BYPASS até TE, memória e transferência.
   Não foram encontrados localmente nesta análise. Não há download pendente em execução.
2. Quando houver autorização explícita para hardware, distinguir óticamente
   frames únicos, repetição e tearing no estado existente, antes de alterar modos.
3. Só depois decidir se o sintoma permite mitigação na app ou requer correção
   do compositor/driver. Não aplicar patches SF/DSI ou sysfs exploratório.
4. Quando a regra offline deixar de se aplicar, rever o PR/comentários atuais,
   publicar este resultado, fazer push dos commits e verificar CI mantendo draft.

Esta continuação é documentação, não autorização para novos comandos no Thor.
