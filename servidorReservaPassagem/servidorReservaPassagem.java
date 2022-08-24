import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
public class servidorReservaPassagem {
    private static class Assento {
        int Idlugar;
        boolean reservado;
        String nomeDoPassageiro;
        LocalDateTime dataHora;
    }
    ServerSocket serverSocket = new ServerSocket(8080);
    ArrayList<Assento> assentos = new ArrayList<>();
    final int vagasDisponiveis = 32;
    final File logFile = new File("logs", "log.txt");
    FileWriter fileWriter = new FileWriter(logFile);
    public static void main(String[] args) throws IOException { new servidorReservaPassagem(); }
    public servidorReservaPassagem() throws IOException {
        for (int i = 0; i < vagasDisponiveis; i++) {
            Assento assento = new Assento();
            assento.Idlugar = i + 1;
            assento.reservado = false;
            assentos.add(assento);
        }
        while (true) {
            //vai ficar tentando pegar conexão enquanto estiver rodando
            try {
                Socket socket = serverSocket.accept();
                //escuta uma conexão e aceita se alguma for encontrada
                new Thread(new Guiche(socket)).start();
                //faz a instancia da Thread
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    private class Guiche implements Runnable {
        Socket socket;
        String status = "ok";
        private Guiche(Socket socket) throws IOException {
            this.socket = socket;
            //inputstream lê para leitura
            InputStream inputLeitura = socket.getInputStream();
            //input vai ser as requisicoes do cabeçalho, onde o output vai ser escrever o html no servidor
            byte[] buffer = new byte[2048];
            //buffer vai ser usado para ler, tamanho usado para renderizar, transforma o byte array numa string usando o buffer do tamanho 0 até o tamnho tamanho
            int tamanho = inputLeitura.read(buffer);
            //no req está a requisição inteira
            String req = new String(buffer, 0, tamanho);
            //quebrar em linhas cada vez q ver um barra n
            String[] line = req.split("\n");
            //string array onde cada lugar é uma das linhas q foram quebradas
            //linha 0 onde vem o get
            String[] line0 = line[0].split(" ");
            String criaBody =
                    "<header>" +
                            "<h2>Viação União Santa Cruz</h2>" +
                            "<a class=\"btn btn-primary\" href='/' role=\"button\">Todos lugares</a>"+
                            "<br>"+
                            "<br>"+
                            "<a class=\"btn btn-primary\" href='/reserva' role=\"button\">Reservar lugar</a>"+
                            "</header>";
            if (line0[1].equals("/")) {
                //se o primero string for só "/" cria essa tabela
                String criaTable = "<table class=\"table table-striped\">";
                for (int i = 0; i < vagasDisponiveis; i += 4) {
                    //pra cada i mostra 4 acentos na tela
                    criaTable += "<tr>";
                    for (int j = 0; j < 4; j++) {
                        Assento assento = assentos.get(i + j);
                        criaTable += "<td style='border: 1px solid black;'>Lugar " + assento.Idlugar + "<br>";
                        if (assento.reservado) {
                            criaTable += "Reservado<br>" + assento.nomeDoPassageiro + "<br>" +
                                    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").format(assento.dataHora);
                            //se o acento já esta reservado mostra o nome, horario e a data
                        } else {
                            criaTable += "Desocupado<br>";
                        }
                        //mostra que o banco está vago
                        criaTable += "</td>";
                        if (j == 1) {
                            criaTable += "<td style='padding: 40px;'></td>";
                        }
                    }
                    criaTable += "</tr>";
                }
                criaTable += "</table>";
                criaBody += "<h2>Lugares</h2>" + criaTable;
            } else if (line0[1].equals("/reserva")) {
                //se a string for /reserva mostra a pagina para fazer reserva
                String criaForm =
                        "<form action='/reserva' method='get'>" +
                                "<h2><label for='nome'>Nome: </label></h2>" +
                                "<input class=\"form-control\" type='text' id='nome' name='nome' required>"+
                                "<h2><label for='lugar'>Lugar: </label></h2>" +
                                "<select class=\"col col-lg-6 form-select form-select-sm\" class=\"form-select form-select-sm\" id='lugar' name='lugar' required>";
                //cria o formulario para pegar os dados do passageiro
                for (int i = 0; i < vagasDisponiveis; i++) {
                    Assento assento = assentos.get(i);
                    if (!assento.reservado) {
                        criaForm += "<option value='" + assento.Idlugar + "'>Assento " + assento.Idlugar + "</option>";
                        //mostra apenas os acentos vagos
                    }
                }
                criaForm +=
                        "</select><br>" +
                                "<br>" +
                                "<button class=\"btn btn-primary\" type=\"submit\">Reservar</button>" +
                                "</form>";
                criaBody += "<h2>Seleção dos Assentos</h2>" + criaForm;
            } else {
                //pegando os dados por get
                String[] get = line0[1].split("[?&]");
                //passando a requisição
                if (get.length > 1) {
                    String[] nome = get[1].split("=");
                    //divide para pegar o nome do passageiro
                    String[] lugar = get[2].split("=");
                    //divide para saber qual é o banco
                    synchronized (assentos.get(Integer.parseInt(lugar[1]) - 1)) {
                        //synchronized tratar para que o mesmo assento não alocado por mais um thread
                        Assento assento = assentos.get(Integer.parseInt(lugar[1]) - 1);
                        if (assento.reservado) {
                            status = "erro";
                            // se o acento não estiver reservado status = "sucesso"
                        } else {
                            assento.reservado = true;
                            assento.nomeDoPassageiro = nome[1].replace("+"," ");
                            assento.dataHora = LocalDateTime.now();
                            status = "sucesso";
                            fileWriter.append(assento.dataHora + " " + assento.Idlugar + " " + assento.nomeDoPassageiro + "\n");
                            fileWriter.flush();
                            //adiciona ao log o que está recebendo
                        }
                    }
                }
            }
            if (!status.equals("ok")) {
                if (status.equals("sucesso")) {
                    criaBody += "<h1>Operação realizada com sucesso.</h1>";
                }
                else {
                    criaBody += "<h1>Erro durante a operação. Por favor, tente novamente.</h1>";
                }
            }
            String html =
                    "<html lang='pt-br'>" +
                            "<head>" +
                            "<meta charset='utf-8'>" +
                            "<meta name='viewport' content='width=device-width, initial-scale=1'>" +
                            "<link rel=\"stylesheet\" href=\"https://cdn.jsdelivr.net/npm/bootstrap@4.0.0/dist/css/bootstrap.min.css\" integrity=\"sha384-Gn5384xqQ1aoWXA+058RXPxPg6fy4IWvTNh0E263XmFcJlSAwiGgFAW/dAiS6JXm\" crossorigin=\"anonymous\">"+
                            "<title>Reserva Dos Assentos</title>" +
                            "</head>"+
                            "<body><div class=\"container\">" + criaBody + "</div></body>" +
                            "</html>";
            OutputStream outputEscreve = socket.getOutputStream();
            //é o que ele está mandando para o cliente
            if (
                line0[1].equals("/") ||
                line0[1].equals("/reserva") ||
                line0[1].matches("^/reserva\\?nome=[0-z]+&lugar=[0-9]+$")
            ) {
                outputEscreve.write((
                        "HTTP/1.1 200 OK\nContent-Type: text/html; charset=UTF-8\n\n"
                        //mandando resposta pro cliente 200 é OK
                ).getBytes(StandardCharsets.UTF_8));
                outputEscreve.write(html.getBytes(StandardCharsets.UTF_8));
            } else {
                if (!status.equals("ok")) {
                    outputEscreve.write((
                            "HTTP/1.1 200 OK\nContent-Type: text/html; charset=UTF-8\n\n"
                    ).getBytes(StandardCharsets.UTF_8));
                    outputEscreve.write(html.getBytes(StandardCharsets.UTF_8));
                } else {
                    outputEscreve.write((
                            "HTTP/1.1 404 Not found\n\nError 404\nNot found"
                    ).getBytes(StandardCharsets.UTF_8));
                    //se deu tudo errado mostra erro 404
                }
            }
            outputEscreve.flush();
            //enviando pro cliente
            socket.close();
            //fechando conexão
        }
        @Override
        public void run() { }
    }
}