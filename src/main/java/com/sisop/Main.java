package com.sisop;


import java.util.LinkedList;
import java.util.Queue;

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
    static int[] pid           = new int[MAX_PROC];
    static int[] ppid          = new int[MAX_PROC];

    static int[] tempoChegada   = new int[MAX_PROC];
    static int[] tempoTotal = new int[MAX_PROC];
    static int[] tempoProcessado   = new int[MAX_PROC];

    static int[] status        = new int[MAX_PROC];
    static int[] tipoIO        = new int[MAX_PROC];

    static int[] ioRestante    = new int[MAX_PROC];

    // ----------------------------------------------------------------
    // FILAS — arrays simples (não circulares) com ponteiro de tamanho
    // ----------------------------------------------------------------
    static final int CAP     = MAX_PROC + 2;

    static Queue<Integer> filaIO = new LinkedList<>();

    static Queue<Integer> filaAlta = new LinkedList<>();

    static Queue<Integer> filaBaixa = new LinkedList<>();




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
            executarPorAteUmQuantum(processoAtual);

            if (isProcessoFinalizado(processoAtual)) status[processoAtual] = FINALIZADO;
            else if (processoSolicitouIO(processoAtual)) {
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

    private static boolean isProcessoFinalizado(int processoAtual) {
        return false;
    }

    private static boolean processoSolicitouIO(int processoAtual) {
        return false;
    }

    private static boolean existemProcessosNaoFinalizados() {
        return false;
    }

    private static void moverNovosProcessosParaFilaAlta(int tempo) {
    }

    private static void atualizarFilasDeIo(int tempo) {
    }

    private static void inicializarProcessos() {
    }

    private static void inicializarFilas() {
    }

    private static void registrarCpuOciosa(int tempo) {
    }

    private static void executarPorAteUmQuantum(int processoAtual) {
    }

    private static void imprimirLinhaDoTempo() {
    }

    private static void imprimirResumoFinal() {
    }

}