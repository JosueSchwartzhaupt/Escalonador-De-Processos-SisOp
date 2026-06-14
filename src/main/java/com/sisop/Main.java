package com.sisop;


import java.util.*;

public class Main {

    // ----------------------------------------------------------------
    // CONSTANTES
    // ----------------------------------------------------------------
    static final int MAX_PROC        = 8;
    static final int QUANTUM         = 3;
    static final int SEED            = 42;
    static final int CPU_MIN         = 5;
    static final int CPU_MAX         = 15;

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

    //TODO: Ver se queremos que um processo possa ter mais de um evento IO
    static int[] tipoIO          = new int[MAX_PROC];
    static int[] inicioProximoIo = new int[MAX_PROC];
    static int[] ioProcessado = new int[MAX_PROC];

    // ----------------------------------------------------------------
    // FILAS — arrays simples (não circulares) com ponteiro de tamanho
    // ----------------------------------------------------------------
    static final int CAP     = MAX_PROC + 2;

    static Queue<Integer> filaIO = new LinkedList<>();

    static Queue<Integer> filaAlta = new LinkedList<>();

    static Queue<Integer> filaBaixa = new LinkedList<>();

    static int tempoAnterior = 0;

    static Map<Integer, List<String>> linhaDoTempo = new HashMap<>();

    public static void main(String[] args) {
        inicializarProcessos();
        inicializarFilas();
        int tempo = 0;

        while (existemProcessosNaoFinalizados()) {
            moverNovosProcessosParaFilaAlta(tempo);
            atualizarFilasDeIo(tempo);

            int processoAtual;

            if (!filaAlta.isEmpty()) processoAtual = filaAlta.remove();
            else if (!filaBaixa.isEmpty()) processoAtual = filaBaixa.remove();
            else {
                registrarCpuOciosa(tempo);
                tempo++;
                continue;
            }

            status[processoAtual] = EXECUTANDO;
            tempo += executarPorAteUmQuantum(processoAtual, tempo);

            if (isProcessoFinalizado(processoAtual)) status[processoAtual] = FINALIZADO;
            else if (processoSolicitouIO(processoAtual, tempo)) {
                status[processoAtual] = BLOQUEADO;
                filaIO.add(processoAtual);
            }
            else {
                status[processoAtual] = PRONTO;
                filaBaixa.add(processoAtual);
            }

        }
        imprimirLinhaDoTempo();
        imprimirResumoFinal();
    }

    private static void inicializarProcessos() {
        registrarNovoProcesso(0,4,DISCO,2);
        registrarNovoProcesso(4,5,NENHUM,-1);
        registrarNovoProcesso(8,7,NENHUM,-1);
        registrarNovoProcesso(12,8,NENHUM,-1);
    }

    private static void inicializarFilas() {

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
        for (int id = 0; id < pidRegistrados; id++) {
            if(status[id] == NOVO && tempoChegada[id] <= tempo){
                processosParaAdicionar.put(id, tempoChegada[id]);
                status[id] = PRONTO;
            }
        }

        processosParaAdicionar.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .forEach(id -> {
                    filaAlta.add(id);
                    loggarNaLinhaDoTempo(tempoChegada[id], "P"+id + " criado → fila ALTA");
                });
    }

    //TODO: Ver se é essa a logica da fila de IO, talves aqui precise fazer round robin também
    private static void atualizarFilasDeIo(int tempo) {
        int tempoDisponivel = tempo - tempoAnterior;

        while (tempoDisponivel > 0 && !filaIO.isEmpty()) {
            int pid = filaIO.peek();

            if (tipoIO[pid] == NENHUM) {
                filaIO.remove();
                continue;
            }

            int tempoUsado = Math.min(
                    duracaoIo(tipoIO[pid]) - ioProcessado[pid],
                    tempoDisponivel
            );

            ioProcessado[pid] += tempoUsado;
            tempoDisponivel -= tempoUsado;

            if (ioConcluido(pid)) {
                finalizarIo(pid, (tempo + tempoUsado));
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

        ioProcessado[pid] = -1;
        tipoIO[pid] = NENHUM;
    }

    private static boolean ioConcluido(int pid) {
        return ioProcessado[pid] >= duracaoIo(tipoIO[pid]);
    }

    private static int duracaoIo(int tipoIo) {
        return switch (tipoIo) {
            case DISCO      -> 8;
            case FITA       -> 3;
            case IMPRESSORA -> 5;
            default         -> 0;
        };
    }

    static String nomeIO(int t) {
        switch (t) {
            case DISCO:      return "DISCO";
            case FITA:       return "FITA";
            case IMPRESSORA: return "IMPRESSORA";
            default:            return "NENHUM";
        }
    }

    private static void registrarCpuOciosa(int tempo) {
        loggarNaLinhaDoTempo(tempo, "CPU ociosa");
    }

    private static int executarPorAteUmQuantum(int processoAtual, int tempo) {
        int tempoQueFalta = tempoTotal[processoAtual] - tempoProcessado[processoAtual];
        int tempoAteInterrupcao = tipoIO[processoAtual] != NENHUM ? inicioProximoIo[processoAtual] -tempo : QUANTUM;
        // O processo ocupa a CPU por até um quantum ou até ocorrer finalização/I/O.
        int tempoParaProcessar = Math.min(tempoQueFalta ,Math.min(tempoAteInterrupcao, QUANTUM));

        tempoProcessado[processoAtual] += tempoParaProcessar;
        loggarNaLinhaDoTempo(tempo, "CPU executa P"+processoAtual + " por " + tempoParaProcessar + " unidades");
        return tempoParaProcessar;
    }

    //TODO: testar também se o tempo De IO foi processado?
    private static boolean isProcessoFinalizado(int processoAtual) {
        //quando o tempo de CPU restante chegar a zero, o processo é encerrado.
        return tempoProcessado[processoAtual] == tempoTotal[processoAtual];
    }

    private static boolean processoSolicitouIO(int processoAtual, int tempo) {
        return tipoIO[processoAtual] != NENHUM && inicioProximoIo[processoAtual] <= tempo;
    }

    private static void imprimirLinhaDoTempo() {
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

    private static void imprimirResumoFinal() {
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
        ioProcessado[pidDoProcesso]    = 0;
    }

    private static int proximoPidDisponivel() {
        return pidRegistrados >= MAX_PROC? -1 : pidRegistrados++;
    }

    private static void loggarNaLinhaDoTempo(int tempo, String mensagem){
        linhaDoTempo
                .computeIfAbsent(tempo, k -> new ArrayList<>())
                .add(mensagem);
    }

}