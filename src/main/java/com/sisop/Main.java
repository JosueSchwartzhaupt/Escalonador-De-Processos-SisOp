package com.sisop;


import java.util.*;

public class Main {

    // ----------------------------------------------------------------
    // CONSTANTES
    // ----------------------------------------------------------------
    static final int MAX_PROC        = 8;
    static final int QUANTUM         = 3;

    // Tipos de I/O
    static final int NENHUM       = 0;
    static final int DISCO        = 1;
    static final int FITA         = 2;
    static final int IMPRESSORA   = 3;

    // Status
    static final int NOVO         = 0;
    static final int PRONTO       = 1;
    static final int EXECUTANDO   = 2;
    static final int BLOQUEADO    = 3;
    static final int FINALIZADO   = 4;

    // Geração randomica de processos
    static final int MAX_CPU        = 15;
    static final int MIN_CPU        = 5;

    // ----------------------------------------------------------------
    // PCB — arrays paralelos
    // ----------------------------------------------------------------
    static int[] pid             = new int[MAX_PROC];
    static int pidRegistrados    = 0;

    static int[] ppid            = new int[MAX_PROC];

    static int[] tempoChegada  = new int[MAX_PROC];
    static int[] tempoTotal      = new int[MAX_PROC];
    static int[] tempoProcessado = new int[MAX_PROC];

    static int[] status          = new int[MAX_PROC];

    static int[] tipoIO          = new int[MAX_PROC];
    static int[] inicioProximoIo = new int[MAX_PROC];
    static int[] tempoIoProcessado = new int[MAX_PROC];
    static int[] instanteEntradaIo = new int[MAX_PROC];

    // ----------------------------------------------------------------
    // FILAS — arrays simples (não circulares) com ponteiro de tamanho
    // ----------------------------------------------------------------

    static Queue<Integer> filaIO = new LinkedList<>();

    static Queue<Integer> filaAlta = new LinkedList<>();

    static Queue<Integer> filaBaixa = new LinkedList<>();

    // ----------------------------------------------------------------
    // METRICAS
    // ----------------------------------------------------------------

    static int[] contPreempcoes = new int[MAX_PROC];
    static int[] instanteFinalizacao = new int[MAX_PROC];
    static int[] tempoDeEspera = new int[MAX_PROC];
    static int cpuOciosaTotal;

    static Map<Integer, List<String>> linhaDoTempo = new HashMap<>();

    // ----------------------------------------------------------------
    static int tempoAnterior = 0;

    private static int duracaoIo(int tipoIo) {
        return switch (tipoIo) {
            case DISCO      -> 8;
            case FITA       -> 3;
            case IMPRESSORA -> 5;
            default         -> 0;
        };
    }

    static String nomeIO(int t) {
        return switch (t) {
            case DISCO -> "DISCO";
            case FITA -> "FITA";
            case IMPRESSORA -> "IMPRESSORA";
            default -> "NENHUM";
        };
    }

    public static void main(String[] args) {
        inicializarProcessos();
        inicializarFilas();
        int tempo = 0;

        imprimirConfiguracoes();
        while (existemProcessosNaoFinalizados()) {
            moverNovosProcessosParaFilaAlta(tempo);
            atualizarFilasDeIo(tempo);

            int processoAtual;

            int tAnterior = tempo;

            if (!filaAlta.isEmpty()) processoAtual = filaAlta.remove();
            else if (!filaBaixa.isEmpty()) processoAtual = filaBaixa.remove();
            else {
                registrarCpuOciosa(tempo);
                tempo++;
                continue;
            }

            status[processoAtual] = EXECUTANDO;
            tempo += executarPorAteUmQuantum(processoAtual, tempo);

            registrarTempoEspera(processoAtual,tempo- tAnterior);

            if (isProcessoFinalizado(processoAtual)){
                status[processoAtual] = FINALIZADO;
                loggarNaLinhaDoTempo(tempo,"P" + processoAtual+ " finalizado");
                instanteFinalizacao[processoAtual]= tempo;
            }
            else if (processoSolicitouIO(processoAtual)) {
                status[processoAtual] = BLOQUEADO;
                instanteEntradaIo[processoAtual] = tempo;
                filaIO.add(processoAtual);
                loggarNaLinhaDoTempo(tempo,"P" + processoAtual + " solicitou I/O " + nomeIO(tipoIO[processoAtual]));
            }
            else {
                status[processoAtual] = PRONTO;
                filaBaixa.add(processoAtual);
                contPreempcoes[processoAtual]++;
                loggarNaLinhaDoTempo(tempo,"P" + processoAtual + " sofreu preempção -> fila BAIXA");
            }

        }
        imprimirLinhaDoTempo();
        imprimirResumoFinal(tempo);
    }

    private static void registrarTempoEspera(int processoAtual, int tempoPassado) {
        for (int id = 0; id < pidRegistrados; id++) {
            if(status[id] == PRONTO && id != processoAtual){
                tempoDeEspera[id] += tempoPassado;
            }
        }
    }

    private static void inicializarProcessos() {
        registrarNovoProcesso(0,4,DISCO,2);
        registrarNovoProcesso(4,5,NENHUM,-1);
        registrarNovoProcesso(8,7,NENHUM,-1);
        registrarNovoProcesso(12,8,NENHUM,-1);
//        gerarProcessosRandomicos(3);
    }

    private static void inicializarFilas() {
        filaAlta.clear();
        filaBaixa.clear();
        filaIO.clear();
    }

    private static boolean existemProcessosNaoFinalizados() {
        return Arrays.stream(status)
                .limit(pidRegistrados)
                .filter(statusDoProcesso -> statusDoProcesso != FINALIZADO)
                .findAny()
                .isPresent();
    }

    private static void moverNovosProcessosParaFilaAlta(int tempo) {
        var processosParaAdicionar = new HashMap<Integer, Integer>();

        // Verificar quais processos novos existem até o tempo atual
        for (int id = 0; id < pidRegistrados; id++) {
            if(status[id] == NOVO && tempoChegada[id] <= tempo){
                processosParaAdicionar.put(id, tempoChegada[id]);
                status[id] = PRONTO;
            }
        }

        // Colocar eles em ordem na fila alta
        processosParaAdicionar.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .forEach(id -> {
                    filaAlta.add(id);
                    loggarNaLinhaDoTempo(tempoChegada[id], "P"+id + " criado → fila ALTA");
                    tempoDeEspera[id] += tempo - tempoChegada[id];
                });
    }

    private static void atualizarFilasDeIo(int tempo) {
        int tempoDisponivel = tempo - tempoAnterior;
        int instanteAtualIo = tempoAnterior;

        // Enquanto ainda tiver tempo não contabilizado e processos esperando na fila de IO
        while (tempoDisponivel > 0 && !filaIO.isEmpty()) {
            int idProc = filaIO.peek();

            // Corrige o tempo disponivel com o tempo de entrada do processo na fila
            tempoDisponivel = Math.min(tempoDisponivel, tempo - instanteEntradaIo[idProc]);

            int tempoUsado = Math.min(
                    duracaoIo(tipoIO[idProc]) - tempoIoProcessado[idProc],
                    tempoDisponivel
            );

            tempoIoProcessado[idProc] += tempoUsado;
            tempoDisponivel -= tempoUsado;
            instanteAtualIo += tempoUsado;

            if (ioConcluido(idProc)) {
                finalizarIo(idProc, (instanteAtualIo));
                tempoDeEspera[idProc] += tempo - instanteAtualIo;
            }
        }

        tempoAnterior = tempo;
    }

    private static void finalizarIo(int pid, int instanteFinalizado) {
        filaIO.remove();

        status[pid] = PRONTO;

        //Ao concluir I/O, retorna para a fila adequada conforme o tipo de dispositivo.
        if (tipoIO[pid] == DISCO) {
            filaBaixa.add(pid);
            loggarNaLinhaDoTempo(instanteFinalizado, "P" + pid + " retornou do DISCO -> fila BAIXA");
        } else {
            filaAlta.add(pid);
            loggarNaLinhaDoTempo(instanteFinalizado, "P" + pid + " retornou de " + nomeIO(tipoIO[pid]) + " -> fila ALTA");
        }
        tempoIoProcessado[pid] = 0;
        tipoIO[pid] = NENHUM;
        inicioProximoIo[pid] = -1;
    }

    private static boolean ioConcluido(int pid) {
        return tempoIoProcessado[pid] >= duracaoIo(tipoIO[pid]);
    }

    private static void registrarCpuOciosa(int tempo) {
        cpuOciosaTotal++;
        loggarNaLinhaDoTempo(tempo, "CPU ociosa");
    }

    private static int executarPorAteUmQuantum(int processoAtual, int tempo) {
        int tempoQueFalta = tempoTotal[processoAtual] - tempoProcessado[processoAtual];
        int tempoAteInterrupcao = tipoIO[processoAtual] != NENHUM ? inicioProximoIo[processoAtual] - tempoProcessado[processoAtual] : QUANTUM;
        // O processo ocupa a CPU por até um quantum ou até ocorrer finalização/I/O.
        int tempoParaProcessar = Math.min(tempoQueFalta ,Math.min(tempoAteInterrupcao, QUANTUM));

        tempoProcessado[processoAtual] += tempoParaProcessar;
        loggarNaLinhaDoTempo(tempo, "CPU executa P"+processoAtual + " por " + tempoParaProcessar + " unidades");
        return tempoParaProcessar;
    }

    private static boolean isProcessoFinalizado(int processoAtual) {
        //Quando o tempo de CPU restante chegar a zero, o processo é encerrado.
        return tempoProcessado[processoAtual] == tempoTotal[processoAtual];
    }

    private static boolean processoSolicitouIO(int processoAtual) {
        return tipoIO[processoAtual] != NENHUM && tempoProcessado[processoAtual] == inicioProximoIo[processoAtual];
    }

    private static void imprimirConfiguracoes() {
        System.out.println("=".repeat(60));
        System.out.println("  Simulador Round Robin com Feedback");
        System.out.printf("  quantum=%d | max_processos=%d%n", QUANTUM, MAX_PROC);
        System.out.println("  I/O: DISCO=8u(→BAIXA) | FITA=3u(→ALTA) | IMPR=5u(→ALTA)");
        System.out.println("=".repeat(60));
        System.out.println();
    }

    private static void imprimirLinhaDoTempo() {
        System.out.println();
        System.out.println("=".repeat(60));
        System.out.println("  LINHA DO TEMPO");
        System.out.println("=".repeat(60));

        linhaDoTempo.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entrada -> {
                    String prefixo = String.format("[t=%03d] ", entrada.getKey());
                    String indentacao = " ".repeat(prefixo.length());

                    for (int i = 0; i < entrada.getValue().size(); i++) {
                        if (i == 0) {
                            System.out.println(prefixo + entrada.getValue().get(i));
                        } else {
                            System.out.println(indentacao + entrada.getValue().get(i));
                        }
                    }
                });
    }

    private static void imprimirResumoFinal(int tempoSimulacao) {
        System.out.println();
        System.out.println("=".repeat(75));
        System.out.println("  RESUMO FINAL");
        System.out.println("=".repeat(75));

        int totalPreempcoes = 0;
        int somaEspera = 0;
        int somaTurnaround = 0;

        System.out.printf("%-5s %-10s %-10s %-13s %-12s %-10s%n",
                "PID", "Chegada", "Fim", "Temp Espera", "Turnaround", "Preemp.");

        System.out.println("-".repeat(75));

        for (int i = 0; i < pidRegistrados; i++) {
            totalPreempcoes += contPreempcoes[i];
            somaEspera += tempoDeEspera[i];

            int turnaround = instanteFinalizacao[i] - tempoChegada[i];
            somaTurnaround += turnaround;

            System.out.printf("P%-4d %-10d %-10d %-13d %-12d %-10d%n",
                    i,
                    tempoChegada[i],
                    instanteFinalizacao[i],
                    tempoDeEspera[i],
                    turnaround,
                    contPreempcoes[i]);
        }

        System.out.println("-".repeat(75));

        System.out.printf("Tempo total da simulação : %d unidades%n", tempoSimulacao);
        System.out.printf("CPU ociosa               : %d unidades%n", cpuOciosaTotal);
        System.out.printf("Total de preempções      : %d%n", totalPreempcoes);
        System.out.printf("Tempo de espera médio    : %.2f%n",
                (double) somaEspera / pidRegistrados);
        System.out.printf("Turnaround médio         : %.2f%n",
                (double) somaTurnaround / pidRegistrados);

        System.out.println("=".repeat(75));
    }

    private static void registrarNovoProcesso(int tempoDeChegada, int tempoDeProcessamentoTotal, int tipoDeIo, int inicioDoProcessamentoIo) {
        int pidDoProcesso = proximoPidDisponivel();
        pid[pidDoProcesso] = pidDoProcesso;

        ppid[pidDoProcesso] = -1;

        tempoChegada[pidDoProcesso]    = tempoDeChegada;
        tempoTotal[pidDoProcesso]      = tempoDeProcessamentoTotal;
        tempoProcessado[pidDoProcesso] = 0;

        status[pidDoProcesso]          = NOVO;

        tipoIO[pidDoProcesso]          = tipoDeIo;
        inicioProximoIo[pidDoProcesso] = inicioDoProcessamentoIo;
        tempoIoProcessado[pidDoProcesso]    = 0;
        instanteEntradaIo[pidDoProcesso]    = -1;
    }

    private static int proximoPidDisponivel() {
        return pidRegistrados >= MAX_PROC? -1 : pidRegistrados++;
    }

    private static void loggarNaLinhaDoTempo(int tempo, String mensagem){
        linhaDoTempo
                .computeIfAbsent(tempo, k -> new ArrayList<>())
                .add(mensagem);
    }

    private static void gerarProcessosRandomicos(int seed) {
        Random random = new Random(seed);
        int tempoChegadaAnterior = 0;

        for (int i = 0; i < random.nextInt(MAX_PROC)+1; i++) {
            int tempoDeChegada = i == 0? tempoChegadaAnterior : (random.nextInt(tempoChegadaAnterior, MAX_CPU) + 1);
            int tempoDeProcessamento = random.nextInt(MIN_CPU, MAX_CPU + 1);
            int tipoDeIo = random.nextInt(NENHUM, IMPRESSORA + 1);
            int inicioDoProcessamentoIo = tipoDeIo == NENHUM? -1 : random.nextInt(1, tempoDeProcessamento);

            registrarNovoProcesso(tempoDeChegada, tempoDeProcessamento, tipoDeIo, inicioDoProcessamentoIo);

            tempoChegadaAnterior = tempoDeChegada;
        }

    }

}