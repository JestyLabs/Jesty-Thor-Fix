# Thor mixed-refresh: revisão do invalid mode

Atualizado: 2026-10-08, exclusivamente offline, research-only.
Resultado completo: [binário exato, traces e limites físicos](THOR-120HZ-EXACT-BINARY-OFFLINE-20261008.md).

## Correção da conclusão anterior

A revisão de 2026-10-07 comparava o AOSP Android 13 e condicionava as suas
conclusões à equivalência do ELF Thor. Essa condição foi agora testada e **é
falsa no ramo mismatch da .377**. A mensagem não implica BAD_VALUE, rejeição
HWC ou uma segunda invocação válida independente no BOTTOM.

**PROVEN** no SurfaceFlinger Build ID `a4e0851419d45662b0fd5cd067b585bf`:

- `initiateModeChange` começa em `0x1241e8`; compara physical ID do objeto
  recebido com o display alvo (`0x124234..0x12424c`).
- O mismatch regista `invalid mode` (`0x124368`), incrementa o contador
  (`0x1243ec..0x1243f4`), guarda o objeto recebido como upcoming, emite alvo FPS
  e chama HWC com `(~incomingHwcConfigId) & 1` (`0x124488..0x1244a4`).
- `setActiveModeInHwcIfNeeded` chama a mesma função uma segunda vez para o
  display guardado por altura 1240 (`0x1b0fd0..0x1b0fec`), com o **mesmo**
  ActiveModeInfo do ativo. O segundo retorno é ignorado.
- O setter conserva a verificação fatal de identidade; esta segunda chamada
  contorna o setter do secundário. Não é preciso um setter aceitar foreign mode.
- A conclusão `updateInternalStateWithChangedMode` atualiza apenas o default.

**OBSERVED**: TOP tem SF 0=60/SF 1=120; BOTTOM SF 0=120/SF 1=60. O erro inferior
mostra SF 1 em entrada e SF 0 no rollback. O ID impresso pertence ao objeto
recebido; não se interpreta pela tabela do display alvo. HWC handle 3 do BOTTOM
não é um config ID. Android Display.Mode.id usa ainda outro namespace.

**INFERRED**: TOP ativo fornece o objeto foreign ao BOTTOM; o remap seleciona
config inferior0=120/1=60. Isso explica os erros e os contadores1 sem corrupção
ou ponteiro obsoleto. A trace é consistente com este caminho; não contém um
token de invocação ou retornos HWC por painel para provar todos os vínculos runtime.

## Limites que se mantêm

`ActiveModeFPS_HWC` é intenção SF. Scopes constraints/Submit não confirmam o
retorno nem o atomic commit. Vsync8333333 global não mede cadência BOTTOM.
O BOTTOM é oficialmente especificado para60 Hz; modos Android/DRM120 não provam
120 frames distintos, nem a especificação substitui medição ótica. Explicar
`invalid mode` não explica por si só tearing ou os dois blinks.

## Proveniência da comparação histórica

A comparação anterior usava [AOSP DisplayDevice.cpp Android 13 QPR3](https://android.googlesource.com/platform/frameworks/native/+/refs/heads/android13-qpr3-c-s2-release/services/surfaceflinger/DisplayDevice.cpp).
Nesse código de referência, o guard retorna antes do contador/HWC. Essa ordem
continua útil como **diferença técnica**, não como descrição da AYN. A página
não foi reconsultada nesta sessão offline; a conclusão nova vem dos bytes locais.

O próximo problema discriminante passou a ser a relação entre o estado do
secundário, o contrato PASS/BYPASS-RAM e a apresentação física. Ver secções4–8
do relatório exato e [handoff PR37](THOR-120HZ-PR37-OFFLINE-HANDOFF-20261008.md).
