## 🚌 RecogTrack - Urban Fleet Vision AI

RecogTrack é uma Prova de Conceito (PoC) avançada que utiliza Inteligência Artificial e Visão Computacional para o rastreio inteligente de frotas de transporte público urbano.

A solução elimina a necessidade de instalação de hardware GPS proprietário nos veículos, utilizando apenas processamento de imagem em tempo real para identificação e extração de dados.

### 🎯 O Desafio Técnico
Rastrear frotas urbanas de forma não invasiva, lidando com variáveis externas como trepidação, diferentes condições de iluminação e alta velocidade de movimentação dos veículos.

### 🛠️ Stack Tecnológica & Arquitetura
Engine: Android Native Integration.

Intelligence: Google ML Kit (Text Recognition & Object Detection).

Hardware Interfacing: CameraX API para controle fino de foco, exposição e análise de frames em buffer.

Image Processing: Implementação de filtros de qualificação de imagem antes da extração de OCR para aumentar a precisão (Confidence Score).

Architecture: Clean Architecture com foco em Reactive Programming para processamento assíncrono de frames sem bloquear a UI Thread.

### 🚀 Funcionalidades Implementadas
Real-time OCR: Extração instantânea de números de ordem e linhas de ônibus através de análise de vídeo.

Object Tracking: Identificação de veículos no fluxo de tráfego.

Edge Computing: Todo o processamento de IA é feito on-device, garantindo baixa latência e economia de dados.