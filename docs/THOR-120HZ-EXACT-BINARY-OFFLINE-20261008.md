# Thor 60/120 Hz: análise offline dos binários exatos, 2026-10-08

Estado: **research-only / PR #37 draft; nenhuma alteração de produção**.
Esta análise explica o `invalid mode` e uma divergência de estado do compositor.
**Ainda não demonstra a causa do tearing nem uma correção para o eliminar.**

## Critério de evidência e âmbito

- **PROVEN**: fluxo, predicado ou estrutura estabelecido diretamente nos bytes locais identificados; não implica execução nessa captura.
- **OBSERVED**: registo runtime realmente reaberto, estado guardado, observação do utilizador ou informação externa, indicando qual destas origens se aplica.
- **INFERRED**: ligação entre factos com suporte explícito, mas sem todos os argumentos/retornos observados.
- **HYPOTHESIS**: mecanismo possível ainda sem teste discriminante.

TOP ICNA3520: oficialmente 120 Hz. BOTTOM CH13726A: oficialmente 60 Hz,
conforme o contexto fornecido pelo utilizador; a página oficial não foi consultada
nesta sessão offline. Esta especificação é relevante e não foi invalidada por uma
tabela Android/DFPS/Linux com 120 Hz. Também não constitui uma medição física
da unidade. Manter seis grandezas distintas: refresh Android anunciado, alvo HWC,
configuração SDM/DRM, transferência DSI, atualização interna do controlador e
frames distintos efetivamente apresentados pelo OLED.

Não se executou ADB, código no Thor, instalação, recolha, escrita de settings/sysfs,
reinício ou operação de rede. Não se alteraram main, tags, versão estável ou runtime.
Os comandos mencionados no framework abaixo são **dados analisados**, não executados.

## 1. Descobertas confirmadas e proveniência

Foram localizados os **22 ELF**, `services.jar`, `framework.jar`, a captura das
12:28 e os dois ficheiros originais das 23:42. Os 22 SHA-256 coincidem tanto com
`32-device-sha256.txt` como com `33-host-sha256.txt` do bundle baseline. Nenhum
ficheiro esperado falta. Foram extraídos Build IDs dos 22 ELF. O vínculo ao
firmware `Thor_V1.0.0.377_20260206_165408_user` é o manifesto/identificação
guardados da recolha; estes ficheiros não autenticam independentemente uma imagem
de firmware assinada e não demonstram o firmware atualmente instalado.

14 dos 22 ELF contêm `.gnu_debugdata` comprimido com LZMA, incluindo SF,
composer e as bibliotecas core/DAL analisadas. Os símbolos desse mini-ELF
permitem delimitar funções, incluindo as não exportadas. Usaram-se pyelftools
0.33 e capstone 5.0.9 já locais, sem descarregar dependências. Os endereços
seguintes são **VA do ELF**, não endereços ASLR runtime. Para os intervalos
apresentados, o segmento PT_LOAD dá file offset numericamente igual à VA;
a ferramenta calcula a conversão em vez de a assumir.

| Artefacto | SHA-256 | ELF Build ID |
|---|---|---|
| `surfaceflinger` | `44c3bed3b6fef384a29c2f5c9ed66813f70617c3832b1c63c1ea13f580c50fdd` | `a4e0851419d45662b0fd5cd067b585bf` |
| composer Qualcomm | `22a5a6a92d00d11849faeb60eec193d04ccec70b1507614d9e111d940418ce30` | `711c3f20f80a8385b3aacc388e842b95` |
| `lib64/libsdmcore.so` | `cc66ec5eed51bede820133e9de8df9c5ae4be875ff04fede3b12be3b8c8f5935` | `a40129ab60d76b985caeccd4df88c93a` |
| `lib64/libsdmdal.so` | `0618865144e82e59f52e7b8d9766f1bada5a4bf372bd70281b608b0c68ec3dec` | `29f0b0ec38d441590f51be89d8fac5fa` |

`services.jar`: `2d2cad9bbfc1e856440e2b99beefacdcdf927981414d8478d0327f20bdfc0903`.
`framework.jar`: `02906b19cf5ccba529d2023b5337e4678f03f7efbd8974d4f2742c49c0517773`.
Os JAR não têm ELF Build ID. Regenerou-se smali das classes relevantes com o
apktool 3.0.3 local, independentemente dos ficheiros Java decompilados anteriores.

Inventário adicional (32 e 64 bits identificados, sem implicar ambos carregados):

| Biblioteca | Build ID lib/32 | Build ID lib64 |
|---|---|---|
| `libdisplayconfig.qti.so` | `4de01731019a25af259ae16f7916c102` | `152eb30af86c0c807f1915664a4c416a` |
| `libqdMetaData.so` | `065df10630e23784b084fdda431dd948` | `687e14e4f5261b398690cd3a280f77af` |
| `libqservice.so` | `14be387f2ba8a39f6704fb5a9ea62ff0` | `dea07e87c34aab00d85071afc59b7107` |
| `libsdm-color.so` | `d78d02f37f59c992d4a9c8dfebd09850` | `99211312b9a379a4a4447429dd45b798` |
| `libsdm-colormgr-algo.so` | `a7df2e8bb437716a11d956e6a52c9668` | `5a6dfd1d11829e71c36614e3c9f22e6f` |
| `libsdm-disp-vndapis.so` | `cd9ad3d4824371d7720baa48e44e74a6` | `f85808b725939807293d4c3461cf2dad` |
| `libsdmcore.so` | `3cd50abd87f7b66124b615cac7a558e2` | `a40129ab60d76b985caeccd4df88c93a` |
| `libsdmdal.so` | `c44aecc04f1b106d813da018e988239a` | `29f0b0ec38d441590f51be89d8fac5fa` |
| `libsdmextension.so` | `a0cac6aa5e91eaba7f78deb2a92826b0` | `6439c7c3bc6f266b790fe4daefd0b22d` |
| `libsdmutils.so` | `f8c81ebb1c694c1493b3fe80f57c0afa` | `211bc1c44236f1c4aff5521b4ca4836c` |

Os inventários completos com hashes e disassembly permanecem em
`C:\Temp\thor-refresh-offline-20261008`. Não foram adicionados binários,
traces, smali proprietário ou dumps ao Git.

## 2. Mecanismo da AYN demonstrado e limites

Há **dois mecanismos distintos** no firmware analisado:

1. **PROVEN — SF replica o pedido do display ativo para um display secundário**,
   identificado no loop pela altura 1240. Reutiliza o mesmo objeto de modo e
   remapeia o HWC config ID quando a identidade física difere (secção 3).
2. **PROVEN — framework coordena política, brilho inferior e `bypass_ram`**.
   `DisplayModeDirector.SettingsObserver` acompanha notificações min/peak,
   lê o brilho secundário e tem um caminho de fade condicionado a ambas.
   O callback `lambda$updateRefreshRateSettingLockedForX6$1` chama a atualização
   de política (que devolve `peak_refresh_rate`), arredonda esse valor,
   compara com 110 (`0x6e`), inverte
   o booleano e constrói `0x0` para >=110 ou `0x1` para <110 no sysfs bypass.
   O caminho inclui brilho zero, atraso 50 ms, escrita bypass, atraso 200 ms e
   restauração animada de 200 ms. `DisplayUtils` procura display interno com
   ID lógico não zero. Isto não é uma medição da frequência do controlador.

**OBSERVED — execução parcial**: logcat contém o observer às 23:42:17.118 e
23:42:22.551/.558, com readback do brilho; às 22.620 há auditoria de tentativa
de escrita em `bypass_ram`, com `permissive=1`. Não há registo inequívoco do
valor efetivamente escrito, retorno da operação, brilho zero/restaurado ou ACK DSI.
O ramo estático não basta para afirmar que todo o fade decorreu. Os dois black
blinks foram relatados pelo utilizador; o primeiro foi no BOTTOM, a atribuição
do segundo não está estabelecida. **INFERRED**: o fade é uma explicação concreta
para o blink inferior. Não demonstrámos correspondência um-a-um com os dois blinks.

Os documentos anteriores conservaram proveniência pública do device tree/driver:
timing nominal inferior 60 Hz, DFPS `<120 60>`, clocks DSI 1011000000/505000000,
`1 -> VID_BYPASS_RAM -> B9 00`, `0 -> VID_PASS_RAM -> B9 11`, com sequências
off/on. **OBSERVED em análise pública anterior, não revalidado nesta sessão**:
não foi encontrado um checkout local desse driver nem um datasheet CH13726A.
A ligação do sysfs ao comando tem suporte histórico; o seu efeito elétrico nesta
unidade continua sem confirmação. Os nomes PASS/BYPASS não demonstram capacidade
ou organização de RAM, repetição de frames, descarte, ping-pong de buffers ou
desacoplamento entre escrita DSI e leitura do OLED. Estas continuam **HYPOTHESIS**.

## 3. Causa exata do `invalid mode`

### Identidades: três namespaces e uma tabela invertida

| Display físico | SF mode ID / HWC config ID | Refresh tabelado | HWC display handle |
|---|---|---|---|
| TOP `4630946441858561667` | 0 / 0 | 60 | 0 |
| TOP | 1 / 1 | 120 | 0 |
| BOTTOM `4630946482288158084` | 0 / 0 | 120 | 3 |
| BOTTOM | 1 / 1 | 60 | 3 |

Tabela **OBSERVED** no dump SF guardado das 12:28. O handle HWC 3 não é um
config ID. Android `Display.Mode.id` TOP 1/2 corresponde a 60/120 nos eventos
DisplayDeviceRepository; não confundir com SF 0/1. O `invalid mode 1` contém o
**ID SF do objeto recebido**, não a interpretação do ID 1 na tabela do BOTTOM.

### O predicado não rejeita neste binário

`DisplayDevice::initiateModeChange`: `0x1241e8..0x124510`, tamanho `0x328`.
Excerto mínimo transcrito do disassembly local; instruções intermédias omitidas:

```text
124228 ldr x8, [x1]             ; mode do ActiveModeInfo
12422c cbz x8, 0x12438c         ; ramo null, não observado no teste
124234 ldr x23, [x8, #8]        ; physical ID do objeto de modo
124244 bic x8, x0, x0, asr #63 ; ID físico do display alvo
124248 cmp x23, x8
12424c b.ne 0x1242f4            ; mismatch físico
...
124368 bl 0x5e56f0             ; __android_log_print: invalid mode
...
1243ec add x1, x22, #0x2a0
1243f0 mov w0, #1
1243f4 bl 0x113fc0             ; incrementa contador por política
124410 str x26, [x22, #0x288]  ; conserva o mode recebido como upcoming
...
124488 ldr w8, [x8]            ; HWC config ID do objeto recebido
12448c mvn w8, w8
124490 and w2, w8, #1         ; (~configId) & 1
124494 ldr x8, [x9, #0x150]
1244a4 blr x8                 ; chamada ao HWC
...
1244d8 ret                    ; retorno da chamada, não BAD_VALUE antecipado
```

No ramo de identidade válida, o contador é incrementado em `0x124250..0x124258`
e o config ID é carregado diretamente em `0x1242ec`. O ramo mismatch também
emite o marcador `ActiveModeFPS_HWC` (caminho `0x124460 -> 0x1244dc -> 0x124468`).
**PROVEN**: o erro numérico, contador e chamada HWC podem pertencer à **mesma
invocação**. A interpretação AOSP de retorno antecipado `BAD_VALUE` não se aplica
ao mismatch da .377. Não extrapolar a segurança do ramo null; não ocorreu aqui.

### Como o objeto do TOP chega ao BOTTOM

`SurfaceFlinger::setActiveModeInHwcIfNeeded`: `0x1b0d98..0x1b121c`.
O loop chama `getHeight` em `0x1b0e78`, compara com `0x4d8` (1240) em
`0x1b0e7c` e guarda o DisplayDevice correspondente no campo SF `+0x8b8`.
O caminho do display ativo obtém o desired ActiveModeInfo via `getDesiredActiveMode`
em `0x1b0ed0` (callee `0x125740`). Depois:

```text
1b0fb4 sub x1, x29, #0x30     ; desired ActiveModeInfo do display ativo
...
1b0fcc bl 0x1241e8           ; inicia no display ativo
1b0fd0 ldr x8, [x19, #0x8b8] ; display secundário guardado
1b0fd4 mov w24, w0           ; guarda APENAS o primeiro retorno
1b0fd8 cbz x8, 0x1b0ff0
1b0fdc sub x1, x29, #0x30    ; exatamente o MESMO ActiveModeInfo
1b0fe0 add x2, sp, #0x20     ; mesmas constraints
1b0fe4 add x3, sp, #8        ; mesmo destino de timeline de saída
1b0fe8 mov x0, x8
1b0fec bl 0x1241e8           ; inicia também no secundário
1b0ff0 cbz w24, 0x1b10c0     ; testa primeiro retorno; ignora segundo
```

**PROVEN**: existe uma alteração específica deste firmware face ao caminho
AOSP usado como referência. A segunda iniciação contorna o setter do secundário
e passa o objeto do ativo. **INFERRED, fortemente suportado pela trace**: com TOP
ativo, BOTTOM recebe TOP SF mode 1 em entrada e TOP SF mode 0 em rollback; a
identidade difere, o log aparece e a inversão escolhe BOTTOM HWC 0=120 ou 1=60.
Não é necessário postular ponteiro obsoleto ou corrupção de IDs para explicar
estes erros. Não se reconstruiu toda a alocação original dos DisplayMode;
está demonstrado o ponto concreto de reutilização indevida entre identidades.

`DisplayDevice::setDesiredActiveMode`, `0x125540`: mantém verificações fatais
para null (`0x125700`) e physical ID incompatível (`0x12571c`). O byte pending
`+0x281` é testado em `0x1255a8`; quando já pendente, atualiza o desired e devolve
false; a primeira mudança marca pending em `0x125640`. O wrapper SF só agenda
quando o setter devolve true (`0x1af168..0x1af180`). Isto mantém aberta uma
explicação desired/pending para o teste sem handoff das 12:28, mas não prova o
valor desse byte nessa execução e não exige uma política SF intermédia.

### Conclusão atualiza apenas o default

`SurfaceFlinger::updateInternalStateWithChangedMode`, `0x1af938..0x1afc38`:
chama `getDefaultDisplayDeviceLocked` em `0x1af980`, lê upcoming desse display
em `0x1af98c/0x1af990` e chama `setActiveMode` em `0x1af9c8`. Não há atualização
equivalente do secundário nesta função. **PROVEN**: essa conclusão é default-only.
**INFERRED**: explica o BOTTOM SF continuar reportado a 60 apesar do alvo HWC120.
Não demonstra nem a aplicação HWC inferior nem a cadência física inferior.

## 4. Traces, Qualcomm e origem do tearing

### Relógios e integridade

`atrace.txt` SHA-256 `0b823204f73e35af0e29c3a33f8b94143f79975389a5d26eec60b8f03a0e6035`;
`logcat.txt` SHA-256 `f454772323e709885d030a078e217d258848c38070cd26ef101ce72605e70da8`.
Atrace linha 16: timestamp `76246.000855`, `realtime_ts=1791412922079`.
Logo `epochSeconds = traceSeconds + 1791336676.078145`.
Esse anchor corresponde a 22:42:02.079 UTC / 23:42:02.079 Lisboa.
Logcat foi interpretado explicitamente com ano 2026 e offset +01:00. O
`parent_ts=31669.007812` da linha 15 pertence a outra relação de relógios e não
foi usado como timestamp desta trace. Um único anchor com resolução de ms não
fornece uma medição independente do drift; casas decimais calculadas não dão
precisão causal de microssegundos entre logcat e atrace.

### Teste das 12:28: reaberto, sem handoff observado

Logcat guardado: política SF diretamente **60/60 -> 120/120**, em 12:28:39.059,
nos dois displays. Rollback 12:28:49.107/.108; contadores da política 120 iguais
a zero. Dumps guardados: TOP SF 0=60, BOTTOM SF 1=60, SDM cur60 e ambos CRTCs60.
Não há atrace deste ensaio. Nenhum handoff normal observado; a localização
exata do bloqueio desired/pending permanece aberta. O relógio host do probe
difere vários segundos do logcat; não se fez pareamento por segundos semelhantes.
**Não houve evidência de uma política SF intermédia 60–120 neste ensaio.**

### Teste das 23:42: timeline por display

Horas de Lisboa derivadas do anchor; arredondadas a ms. SF PID 2319,
composer PID **2305**. Os IDs de hardware só são atribuídos onde estão explícitos.

| Hora | Atrace s / logcat linha | Display / IDs | Evento e resultado disponível |
|---|---|---|---|
| 23:42:17.246 | logcat 25258 | TOP, SF 1 | política 120; anterior SF 0=60 |
| 23:42:17.248 | logcat 25262 | BOTTOM, SF 0 | política 120; anterior SF 1=60 |
| 23:42:17.246 | 76261.168298/.168305 | não atribuído | duas scopes desired; sem argumentos/retornos |
| 23:42:17.251 | 76261.173342, linha67672 | TOP, alvo 120 | marcador SF; config 1 inferido pelo ELF/tabela |
| 23:42:17.252 | logcat25266 | BOTTOM, objeto SF 1 | invalid identity; não prova rejeição |
| 23:42:17.252 | 76261.174043, linha67675 | BOTTOM, alvo 120 | marcador SF; config 0 inferido pelo ELF/remap |
| 23:42:17.268 | logcat25267 | TOP, Android 2 | framework reporta120 |
| 23:42:22.652 | logcat25277/25281 | TOP SF 0 / BOTTOM SF 1 | política 60; cada contador anterior=1 |
| 23:42:22.660 | 76266.581670, linha98901 | TOP, alvo 60 | config 0 inferido pelo ELF/tabela |
| 23:42:22.660 | logcat25285 | BOTTOM, objeto SF 0 | invalid identity no rollback |
| 23:42:22.660 | 76266.581902, linha98906 | BOTTOM, alvo 60 | config 1 inferido pelo ELF/remap |
| 23:42:22.669 | logcat25286 | TOP, Android 1 | framework reporta60 |

Eventos **sem ID físico** não são transformados em confirmações por display:

| Atrace s | Função / amostra | Alcance |
|---|---|---|
| 76261.173598 / .174238 | duas `SetActiveConfigWithConstraints` (28/25 us) | entrada/saída, argumentos e status ausentes |
| 76261.175193 / .179487 | duas `ProcessActiveConfigChange` | processamento diferido entrou |
| 76261.175206 / .179493 | duas `SubmitDisplayConfig` (1159/455 us) | scopes com ReconfigureDisplay; sem retorno/ID |
| 76261.189366 | `updateInternalStateWithChangedMode` | conclusão entrou; ELF mostra default-only |
| 76261.199708 / .199776 | VsyncPeriod / onComposerHalVsync 8333333 ns | global, não prova cadência BOTTOM |
| 76266.581776 / .581966 | duas constraints para rollback | sem config/retorno explícito |
| 76266.582396 / .583884 | duas Submit (258/171 us) | não prova commit físico |
| 76266.589784 | conclusão | função entrou |
| 76266.597436 / .597520 | períodos16666666 ns | global |

**INFERRED**: a sequência das duas chamadas HWC corresponde às iniciações TOP
e secundária do ELF; não há ID no nome das scopes para confirmar individualmente
os dois Submit. O log e marcador inferiores coincidem ao ms e o ELF permite que
sejam da mesma invocação; não se afirma identidade runtime de invocação sem token.
O dump in-window TOP 120/BOTTOM 60 referido nas notas anteriores não foi reencontrado
como artefacto independente neste diretório (só atrace/logcat). Esse estado inferior
é **OBSERVED no relato anterior**, não uma nova leitura. O evento TOP 120 foi
confirmado diretamente no logcat; ausência de evento BOTTOM não mede o BOTTOM.

### Fronteiras por display

| Estado | TOP | BOTTOM |
|---|---|---|
| SF_REQUESTED | OBSERVED: política/alvo 120 | OBSERVED: política/alvo 120 |
| SF_VALIDATED | INFERRED: caminho normal do ativo | identidade falha; PROVEN no ELF que continua pelo ramo vendor |
| HWC_REQUESTED | alvo explícito; config 1 INFERRED | alvo explícito; config 0 INFERRED pelo remap |
| HWC_ACCEPTED | não confirmado por retorno/ID runtime | não confirmado por retorno/ID runtime |
| SDM_APPLIED | scopes anónimas não confirmam por display | scopes anónimas não confirmam por display |
| DRM_ACTIVE na janela 23:42 | não há dump independente reaberto | não há dump independente reaberto |
| PHYSICAL_CADENCE / frames distintos | não medidos | não medidos |

O encadeamento não é uma promoção automática: no BOTTOM a etapa de validação
de identidade falha, mas a implementação proprietária prossegue deliberadamente.

### Qualcomm: onde termina a prova de aplicação

| Função exata | VA / operação verificada | O que não demonstra |
|---|---|---|
| composer `SetActiveConfigWithConstraints` | `0x72708..0x72f98`; valida tabela/constraints; guarda pending config em `+0x20c` (`0x72dc0`), tempos `+0x210/+0x218`; sucesso enfileira | aceitação não equivale a commit |
| composer `ProcessActiveConfigChange` | `0x6e8f8..0x6f5d8`; pending/time gates; Submit inlined; chamada virtual SDM em `0x6eafc`, status testado em `0x6eb00`; atualiza índice em `0x6eb50` | trace não fornece esse status/ID |
| SDM `DisplayBuiltIn::SetActiveConfig` | `0x5a29c..0x5a330`, delega Base | não é medição |
| SDM `DisplayBase::SetActiveConfig` | `0x4013c..0x4033c`; HW SetDisplayAttributes em `0x4027c`; teste retorno `0x40284`; reconfigure `0x402d0` | reconfigure não é scanout |
| SDM `DisplayBuiltIn::SetRefreshRate` | `0x5a9e4..0x5adc8`; caminho DFPS separado, suporte/estado/min/max gates | não foi encontrado esse nome na trace; não prova que ocorreu |
| DAL `HWDeviceDRM::SetDisplayAttributes` | `0x305e4..0x307bc`; valida índice, SetDisplaySwitchMode `0x30650`, prepara atributos/dirty flag | não faz atomic commit nesta função |
| DAL `HWPeripheralDRM::SetDisplayAttributes` | `0x3eed8..0x3ef98`; chama base em `0x3ef4c`, termina com 0 no caminho normal | descarta retorno base nesse caminho; não prova erro ocorrido |
| DAL `SetDisplaySwitchMode` | `0x36604..0x370a0`; seleciona estrutura de modo/clock, GetSupportedBitClkRate `0x36c20` | clock escolhido em software não mede transferência DSI |
| DAL `AtomicCommit` | `0x39848..0x39fbc`; SetupAtomic `0x39930`; commit virtual `0x399ec`; guarda status `0x399fc`, testa `0x39a84`; cria release/retire fences `0x39a28/0x39a70` | captura não contém status, fence signal ou buffer por CRTC |
| DAL `HWPeripheralDRM::Commit` | `0x3fcac..0x4024c`; base Commit `0x3fdf8`; guarda/testa retorno `0x3fe08/0x3fe48` | não assumir ausência de sincronização só porque houve tearing relatado |
| DAL `GetSupportedBitClkRate` | `0x370a0..0x372e0`; procura clock pedido na lista do modo e tem fallback para clock tabelado | não mede a frequência DSI nem o refresh interno |
| DAL `VSyncHandlerCallback` | `0x4862c..0x48764`; converte segundos/microssegundos em timestamp ns (`0x486e4..0x48700`) e encaminha callback | um timestamp recebido não prova frames distintos ou atualização ótica |

A config inferior calculada é válida na tabela guardada: 0=120 na entrada e
1=60 no rollback. **INFERRED**: o processamento/Submit/Reconfigure subsequente é
consistente com aceitação e avanço no SDM. **Não há confirmação runtime por
display de HWC_ACCEPTED, SDM_APPLIED, DRM_ACTIVE ou PHYSICAL_CADENCE** nessa janela.

Na trace inteira há 2023 amostras globais VsyncPeriod; cada um dos quatro contadores
PrevFramePending/Missed/HwcFrameMissed/GpuFrameMissed tem 509 amostras, todas 0;
1012 scopes de AtomicCommit, DRMAtomicReq::Commit e presentAndGetReleaseFences.
Isto demonstra atividade de composição/commit e ausência de misses nesses
contadores amostrados. Não exclui tearing, perda de registos, frames repetidos ou
fences incorretas num controlador. O analisador preserva estes limites.

### Hipóteses de tearing, por suporte mecanístico

| Ordem | Hipótese | Suporte existente | Prova discriminante em falta |
|---|---|---|---|
| 1 | SF/HWC secundário divergente afeta pacing, timeline ou estado de transição | PROVEN: objeto foreign, remap, retorno secundário ignorado, mesmo output timeline, conclusão default-only | relação entre este estado e buffer/latch/vsync/fence do BOTTOM durante uma linha de tearing; divergência isolada também pode ser só bookkeeping |
| 2 | PASS-RAM/DFPS/DSI desacopla escrita e leitura interna e permite atualizar memória durante leitura | framework seleciona bypass por refresh; driver histórico nomeia PASS/BYPASS e clocks | datasheet/driver exato, significado dos comandos, alinhamento TE/vblank e evidência de conflito leitura/escrita; repetição60/120 sozinha não produz necessariamente tearing |
| 3 | present/release fence ou troca de buffers fora do intervalo seguro | cadeia exata usa commit e fences; captura não inclui sinalização | timestamps de aquisição, sinalização e scanout por CRTC; não há erro de fence demonstrado |
| 4 | pacing da app/conteúdo produz judder confundido com tearing | falta vídeo/padrão identificado; contadores globais0 não resolvem | distinguir descontinuidade horizontal intra-frame de repetição/stutter entre frames |

Uma arquitetura pacesetter/follower pode usar um relógio120 e entregar conteúdo60
regularmente sem tearing. Não se identificou nesta captura qual evento global
serve de relógio físico inferior. Não se atribui a causa ao log `invalid mode`
nem se declara que corrigir esse log eliminaria o sintoma.

## 5. Possíveis soluções e viabilidade na app

| Abordagem | Viabilidade / benefício plausível | Risco hardware / root ou firmware | Jesty Thor Fix |
|---|---|---|---|
| pacing60 nas superfícies da própria app, com cadência regular | alta; pode reduzir judder próprio, não controla jogos nem buffers do compositor | baixo; sem root para o próprio conteúdo | implementável apenas se o sintoma for desse conteúdo; não é fix global demonstrado |
| oferecer opção explícita60/60 pelo caminho stock | moderada como mitigação se um teste futuro mostrar desaparecimento do tearing | ativa fade/bypass stock; root/permissão privilegiada para política global; não chamar isento de risco | possível opção opt-in futura, não implementada nem validada aqui |
| TOP 120/BOTTOM 60 independente | não demonstrada neste firmware: SF replica o modo ativo ao secundário | API normal não garante isolamento; bypass por root pode dessincronizar driver | não prometer um toggle seguro apenas por escrever settings por display |
| corrigir objeto/counter/completion/timeline secundários no SF | tecnicamente plausível para inconsistência; benefício no tearing desconhecido | firmware/compositor proprietário; elevado risco operacional | fora de uma correção normal da app; sem patch produzido |
| alterar DSI clock, DFPS, RAM ou fences diretamente | baixa sem contrato/datasheet e medição causal | root/driver/firmware; risco alto ou desconhecido para painel/estabilidade | não implementar a partir destas evidências |

Não há neste ponto uma correção segura de tearing global demonstrada para a app.
O controlo real da troca de buffers/scanout/RAM pertence ao compositor/driver/controlador.

## 6. Trabalho efetuado, testes e estado do PR

Ferramentas originais, sem copiar implementação de terceiros:

- `inspect-thor-refresh-elf.py`: SHA/Build ID/manifesto, mini-debug symbols,
  VA->file offset e disassembly AArch64 com nomes PLT.
- `InspectFrameworkClass.java`: lê DEX do JAR local com bibliotecas apktool;
  não carrega nem executa código Android.
- analisador de bundles: seleciona artefactos de captura, ignora documentação,
  relatórios e binaries; conserva hashes/proveniência; regex runtime exige tag/prefixo.
- `analyze-thor-refresh-timeline.py`: eventos por display e UNASSIGNED, namespaces
  explícitos, scopes por thread, durações sem inventar retornos, anchor por trace,
  amostras globais sem converter DRM/vsync em cadência óptica.
- analisador de strings: apenas ELF magic, offsets e hash; não reingere o relatório.
- modelo Java: DRM reportado separado de refresh óptico e frames distintos medidos;
  lookup de modo por identidade física+SF ID; baseline60 corrigida.
- regressões sintéticas: IDs invertidos, null/invalid, chamadas HWC distintas,
  falta/inconsistência de clock sync, documentação, namespaces e limites das fences.

Validação final e commits são registados no handoff e na resposta da sessão.
O workflow host já executa o contrato refresh; agora explicita a verificação de
Python. CI remoto não foi executado, por ser uma sessão offline. Não é necessário
gerar APK para uma alteração exclusivamente de investigação.

O checkout estava limpo, na branch pedida. Foi avançado **apenas por fast-forward
local** para o ref origin já em cache (`b33762d`); não houve fetch/push. O corpo
local guardado do PR foi lido; não foi encontrado arquivo integral dos comentários.
Por isso não se afirma ter lido o PR remoto completo ou ter confirmado o seu estado
atual. Há comentário técnico preparado no handoff; não publicado devido à regra offline.

## 7. Trabalho pendente

Offline, com artefactos adicionais já existentes: fonte exata do driver/firmware,
datasheet legítimo do CH13726A e implementação DRMAtomicReq por trás da interface
virtual podem definir o contrato PASS/BYPASS e TE. Não foram encontrados na busca
local dirigida; os 22 ELF não incluem uma implementação identificada desse driver
kernel. Analisar mais strings destes mesmos ELF não resolve a cadência óptica.

Sem recolher nada agora, uma validação física posterior precisará de padrão com
frame IDs únicos por display e registo ótico capaz de distinguir120 atualizações,
60 frames repetidos e uma linha de tearing; relógios, per-CRTC vblank/TE,
buffers/fences e config ativa devem ser correlacionados se disponíveis. Não é uma
autorização para novos comandos, alterações de refresh ou recolha nesta sessão.

## 8. Próximo passo concreto

O maior valor **offline** é obter/analisar, numa sessão com acesso autorizado,
o contrato exato do CH13726A e o código do sysfs bypass correspondente à .377,
seguindo o comando B9 até TE/transferência/refresh interno. O maior valor com
hardware será uma observação ótica com frame IDs no estado já existente, antes de
qualquer nova alteração de modo. A decisão entre mitigação na app e correção
proprietária depende de demonstrar em que fronteira um frame se torna incoerente.

### Reprodução exclusivamente local

Os caminhos seguintes identificam artefactos já guardados. Os outputs ficam fora do Git.

```powershell
python scripts/inspect-thor-refresh-elf.py C:\Temp\thor-refresh-baseline-20261007-rerun\binaries --deps C:\Temp\thor-re-tools --manifest C:\Temp\thor-refresh-baseline-20261007-rerun\32-device-sha256.txt --output C:\Temp\thor-refresh-offline-20261008\elf-inventory.json
python scripts/inspect-thor-refresh-elf.py C:\Temp\thor-refresh-baseline-20261007-rerun\binaries\system\bin\surfaceflinger --deps C:\Temp\thor-re-tools --disassemble 0x1241e8 0x124510 --output C:\Temp\thor-refresh-offline-20261008\sf-initiate.txt
python scripts/analyze-thor-refresh-timeline.py C:\Temp\thor-refresh-trace-20261007\atrace.txt C:\Temp\thor-refresh-trace-20261007\logcat.txt --logcat-year 2026 --logcat-utc-offset +01:00 --output C:\Temp\thor-refresh-offline-20261008\timeline-2342.json
javac -cp tools/apktool.jar -d C:\Temp\thor-refresh-offline-20261008 research/refresh/InspectFrameworkClass.java
java -cp 'tools/apktool.jar;C:\Temp\thor-refresh-offline-20261008' InspectFrameworkClass C:\Temp\thor-refresh-framework-20261007\services.jar 'DisplayModeDirector$SettingsObserver' C:\Temp\thor-refresh-offline-20261008\framework-smali
```

As referências públicas nos documentos históricos são comparação/proveniência
anterior; não foram consultadas pela rede nesta análise. As conclusões novas
acima dependem dos artefactos locais identificados, não de presumir equivalência AOSP.
