# 🚌 RecogTrack — Urban Fleet Vision AI

> **PoC** · Android · ML Kit · CameraX · Firebase

Prova de Conceito que demonstra ser possível **rastrear frotas de transporte público urbano sem equipar os veículos** — usando apenas visão computacional e processamento de IA on-device em um smartphone Android.

---

## 🎯 O Problema

Sistemas tradicionais de rastreio de frotas exigem instalação de hardware GPS em cada veículo — alto custo, logística complexa e dependência de fornecedores.

**A hipótese:** uma câmera, um modelo de detecção e OCR são suficientes para identificar e registrar ônibus em tempo real, a partir de um ponto fixo ou móvel, sem tocar no veículo.

---

## 💡 A Solução

```
Frame de câmera
    └── Detecção de objeto (TFLite custom model)
          └── Qualificação de imagem (contraste + GPUImage)
                └── Classificação (Custom ImageLabeler)
                      └── OCR (ML Kit Text Recognition)
                            └── Matching com base de linhas
                                  └── Registro no Firestore
```

O pipeline roda **inteiramente on-device**, sem depender de servidor para inferência. Apenas o dado final (número da linha + timestamp) é enviado à nuvem.

---

## 🛠️ Stack & Decisões Técnicas

| Camada | Tecnologia | Decisão |
|---|---|---|
| Câmera | **CameraX + Camera2 Interop** | Controle manual de AE/AWB para estabilizar frames em movimento |
| Detecção | **ML Kit Object Detection** + modelo `.tflite` customizado | Modelo treinado para veículos urbanos |
| Classificação | **ML Kit Custom Image Labeler** | Filtra falsos positivos antes do OCR (reduz custo de processamento) |
| OCR | **ML Kit Text Recognition** | Extrai número e nome da linha diretamente do painel do veículo |
| Pré-processamento | **GPUImage + ColorMatrix** | Aumenta contraste antes do OCR para melhorar o confidence score |
| Persistência | **Firebase Firestore** | Registro de passagem com timestamp por ponto |
| Plataforma | **Android (Java)** | CameraX Lifecycle + `ImageAnalysis.Analyzer` assíncrono |

---

## ⚙️ Como funciona na prática

1. A câmera captura frames contínuos via `ImageAnalysis`.
2. O modelo de detecção localiza veículos no frame e retorna bounding boxes.
3. A região detectada passa por **enhancement de imagem** (contraste e brilho) antes do labeling.
4. O `ImageLabeler` confirma se é um ônibus — evitando OCR desnecessário em outros objetos.
5. O OCR extrai o texto visível no painel frontal e faz matching com a base de linhas conhecidas.
6. A linha identificada é exibida na UI e registrada no Firestore com o timestamp da passagem.

---

## 🧠 Principais Aprendizados Técnicos

- **Transformação de coordenadas** entre o espaço do sensor, do buffer de análise e da view de preview é crítica para alinhar bounding boxes corretamente com a imagem exibida ao usuário.
- **Encadeamento de modelos** (detect → label → OCR) reduz significativamente chamadas desnecessárias e melhora a performance geral do pipeline.
- **Controle manual de câmera** via Camera2 Interop (desabilitar AE e AWB) foi essencial para estabilizar a qualidade dos frames em cenas com tráfego em movimento.
- O uso de **`STREAM_MODE`** no detector permite análise contínua com baixa latência, ao custo de precisão de bounding box — trade-off aceitável para uma PoC de rastreio.

---

## 📁 Estrutura Relevante

```
Home.java
 ├── initViews()           — referências de UI
 ├── initMlKit()           — inicialização de detectores e modelos
 ├── YourAnalyzer          — ImageAnalysis.Analyzer: captura e transforma frames
 ├── analizer()            — dispara o detector de objetos
 ├── handleDetections()    — orquestra o pipeline detect → label → OCR
 ├── label()               — classifica a região detectada
 ├── textRecognizerByImage() — executa OCR na região classificada
 └── getLine()            — matching fuzzy com a base de linhas
```

---

## 🚧 Status

> **PoC funcional** — pipeline completo rodando em dispositivo físico. Somente para
> **exploração técnica**.

### Veredito

- **Viabilidade técnica**: é possível rastrear ônibus usando apenas visão computacional on-device.

### Próximas vertentes de melhoria

- **Hardware de câmera dedicado — Global Shutter vs Rolling Shutter:**
  - Smartphones utilizam sensores CMOS com **Rolling Shutter**: a imagem é capturada **linha por linha**, de cima para baixo. Como existe uma diferença de tempo real entre a leitura da primeira e da última linha do sensor, objetos em movimento rápido — como um ônibus em tráfego — sofrem **distorção geométrica** (efeito de inclinação). Isso deforma o painel frontal do veículo antes mesmo do OCR, reduzindo diretamente o confidence score do reconhecimento de texto.

  - Câmeras com **Global Shutter** expõem e leem **todos os pixels simultaneamente**, em um único instante, eliminando completamente esse artefato. É o padrão em sistemas de visão industrial (Basler, FLIR) e leitura automática de placas (ALPR). Migrar para um hardware com sensor global shutter seria o próximo passo natural para aumentar a taxa de acerto do pipeline em condições reais de tráfego.
---

## 📄 Licença

Projeto pessoal para exploração técnica. Não destinado a uso em produção neste estágio.
