package packageClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;

public class SpaceRun extends Application{
//------------------------------------------------------------------------//
//																		  //
//								CONSTANTES								  //
//	  																	  //
//------------------------------------------------------------------------//
	protected static final int PORT=2019;
	protected static final int ve_radius = 30;
	protected static final int ob_radius = 50;
	protected static final int pi_radius = 20;
	protected static final int server_tickrate = 100; // 100 ms = 0.1s -> frequence de 10
	private static final double demih = 350;
	private static final double demil = 450;

//------------------------------------------------------------------------//
//																		  //
//								DATA CLIENT								  //
//																		  //
//------------------------------------------------------------------------//

	private String name;
	private Player myself;
	private Map<String,Player> player_list = new HashMap<>(); // le client courant fait partie de la liste
	//private Point target;
	private boolean isPlaying = false;
	private ArrayList<Commands> cumulCmds = new ArrayList<>();
	private ArrayList<Point> obstacles_list = new ArrayList<>();
	private ArrayList<Point> pieges_list = new ArrayList<>();
	private ArrayList<Point> targets_list = new ArrayList<>();
	private int next_target = 0;

//------------------------------------------------------------------------//
//																		  //
//							JAVAFX VARIABLES							  //
//	  																	  //
//------------------------------------------------------------------------//


	//JAVAFX
	private Stage primaryStage;
	//JavaFX Lobby
	private GridPane lobbyPane;
	private Scene lobbyScene;
	//JavaFX Main
	private HBox mainPane;
	private Scene playScene;
	//Canvas
	private Canvas canvas;
	private GraphicsContext ctx;
	private Drawer drawer;
	//Others
	private VBox right;
	//desc
	private Text main_score;
	private Text main_pieges;
	//listplayers
	//chatbox
	private TextFlow received;

//------------------------------------------------------------------------//
//	 																	  //
//						VARIABLES COMMUNICATION	C/S						  //
//	  																	  //
//------------------------------------------------------------------------//

	//private Client c;
	private BufferedReader inchan;
	private PrintStream outchan;
	private Socket sock;
	private Receive r;
	private Timer serverTickrateTimer = new Timer();
	private SendNewComTask serverTickrateTask;



//------------------------------------------------------------------------//
//	 																	  //
//							GETTERS/SETTERS								  //
//	  																	  //
//------------------------------------------------------------------------//
	public GraphicsContext getGraphicsContext() {return ctx;}
	public Map<String,Player> getPlayer_list() {return  player_list;}
	public Player getMyself() {return myself;}
	//public Point getTarget() {return target;}
	public double getDemih() {return demih;}
	public double getDemil() {return demil;}
	public ArrayList<Point> getObstacles_list() {return obstacles_list;}
	public ArrayList<Point> getPieges_list() {return pieges_list;}
	public ArrayList<Point> getTargets_list() {return targets_list;}
	public int get_next() {return next_target;}

	/*public void init() {
		this.score = 0;
		this.player_list = new HashMap<>();
		this.target = null;
		this.isPlaying = false;
		this.cumulCmds = new ArrayList<>();
	}*/



//------------------------------------------------------------------------//
//	 																   	  //
//							MAJ PLAYERS									  //
//	 																	  //
//------------------------------------------------------------------------//

	public void move(Ship p){//simule le monde thorique, a revoir avec -demih et -demil
		if(p.get_posX() > demil) p.set_posX(-demil+p.get_posX()%demil);
		if(p.get_posY() > demih) p.set_posY(-demih+p.get_posY()%demih);
		if(p.get_posX() < -demil) p.set_posX(demil-p.get_posX()%demil);
		if(p.get_posY() < -demih) p.set_posY(demih-p.get_posY()%demih);
	}
	public void onUpdate() {//met à jour les positions des joueurs à chaque
		if (isPlaying) {
		updateListPlayer();
		ctx.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
		ctx.drawImage(new Image("images/space.png"), 0, 0, canvas.getWidth(),canvas.getHeight());
		drawer.drawPieges();
		drawer.drawTargets();
		drawer.drawPlayers();
		drawer.drawObstacles();
		}else {
			ctx.drawImage(new Image("images/space.png"), 0, 0, canvas.getWidth(),canvas.getHeight());
			updateListPlayer();
			drawer.drawObstacles();
			ctx.setStroke(Paint.valueOf("white"));
			ctx.setFont(new javafx.scene.text.Font("Verdana", 50));
			ctx.strokeText("Waiting for the session ... ", 150, 350);
		}
	}
	@SuppressWarnings("unchecked")
	private void updateListPlayer() {
		ListView<String> aff_list_players = (ListView<String>)right.getChildren().get(1);
		List<String> list = new ArrayList<>();
		player_list.forEach((k,v) -> {
			String player_desc = "Player : "+k+" | Score : "+v.getScore();
			list.add(player_desc);
		});
		ObservableList<String> newItems = FXCollections.observableList(list);
		aff_list_players.setItems(newItems);
		main_pieges.setText("Pièges restants : "+myself.getNb_pieges());
	}
	private void updateMyscore() {
		main_score.setText("Score : "+ player_list.get(name).getScore());
	}
	private String whoIsWinner() {
		String winner="";
		int best_score = 0;
		for (Map.Entry<String, Player> entry : player_list.entrySet()) {
			if (entry.getValue().getScore()>best_score) {
				best_score = entry.getValue().getScore();
				winner = entry.getKey();
			}
		}
		return winner;
	}
	private void resetScores() {
		player_list.forEach((k,v) -> {
			v.setScore(0);
		});
	}
//------------------------------------------------------------------------//
//																		  //
//							AFFICHAGE JAVAFX							  //
//	 																	  //
//------------------------------------------------------------------------//
	@Override
	public void start(Stage primaryStage) throws Exception { //CE QUI EST LANCE PAR LAUNCH
		this.primaryStage = primaryStage;
		try {
			sock = new Socket (InetAddress.getByName("127.0.0.1"),PORT);
			inchan = new BufferedReader(new InputStreamReader(sock.getInputStream()));
			outchan = new PrintStream(sock.getOutputStream());
			System.out.println("Connection established : "+sock.getInetAddress()+" port : "+sock.getPort());
			initializeLobby();
			primaryStage.show();
		}catch (IOException e) {
			System.err.println(e);
			sock.close();
		}
	}
	public void initializeMain() throws IOException {

		mainPane = (HBox) FXMLLoader.load(getClass().getResource("main.fxml"));
		playScene = new Scene(mainPane, 1200, 700);
		//-----------------DESSIN---------------------------------
		canvas = (Canvas)mainPane.getChildren().get(0);
		ctx = canvas.getGraphicsContext2D();
		drawer = new Drawer(this);
		//--------------RIGHTPANEL-----------------------------
		//*******Description*********
		right = (VBox)mainPane.getChildren().get(1);
		Pane descJoueur = (Pane)right.getChildren().get(0);
		Text main_username = (Text)descJoueur.getChildren().get(0);
		main_username.setText("User : "+name);
		main_score = (Text)descJoueur.getChildren().get(1);
		main_score.setText("Score : "+ myself.getScore());
		main_pieges = (Text)descJoueur.getChildren().get(2);
		main_pieges.setText("Pièges restants : "+myself.getNb_pieges());
		Button exit = (Button)descJoueur.getChildren().get(3);
		exit.setOnAction(e -> {
			r.setRunning(false);
			sendExit(name);
			try {
				serverTickrateTask.cancel();
				inchan.close();
				outchan.close();
				sock.close();
				System.exit(0);
			} catch (IOException e1) {
				System.out.println("Error : EXIT");
			}});
		
		//********CHAT************

		ScrollPane scrollpane = (ScrollPane)right.getChildren().get(2);
		received = (TextFlow)scrollpane.getContent();
		HBox chatbox = (HBox)right.getChildren().get(3);
		TextField to_send = (TextField)chatbox.getChildren().get(0);
		to_send.setOnMouseClicked(e -> {
			to_send.clear();
			to_send.setStyle("-fx-text-inner-color: black;");
		});
		Button send = (Button)chatbox.getChildren().get(1);
		send.setOnAction(e -> {
			String mess_to_send = to_send.getText();
			to_send.clear();
			if(mess_to_send.length() > 0) {
				String[] message = mess_to_send.split("\\/");
				if(message[0].equals("dm")) {
					if(player_list.containsKey(message[1])) {
						if(message[1].equals(name)){
							to_send.setStyle("-fx-text-inner-color: red;");
							to_send.setText("Cannot send dm to yourself");
						}else {
							Text t = new Text("to:"+message[1]+">"+message[2]+"\n");
							t.setFill(Color.DARKGREEN);
							Platform.runLater(new Runnable() {
					            @Override public void run() {
					            	received.getChildren().add(t);
					            }
					        });
							sendPEnvoi(message[1], message[2]);
						}
					}else {
						to_send.setStyle("-fx-text-inner-color: red;");
						to_send.setText(message[1]+" n'existe pas");
					}
				}else {
					Platform.runLater(new Runnable() {
			            @Override public void run() {
			            	received.getChildren().add((new Text("you>"+message[0]+"\n")));
			            }
			        });
					sendEnvoi(message[0],name);
				}
			}});
		updateListPlayer();
		
		//------------------FIX POSITION---------------------------
		new AnimationTimer(){
			public void handle(long currentNanoTime){onUpdate();}
		}.start();
		//*************EVENT HANDLER*************
		playScene.setOnKeyPressed(e -> {
			if (e.getText().equals("z")) {
				cumulCmds.add(Commands.thrust);
			}
			if (e.getText().equals("d")) {
				cumulCmds.add(Commands.clock);
			}
			if (e.getText().equals("q")) {
				cumulCmds.add(Commands.anticlock);
			}
			if (e.getText().equals("x")) {
				if(myself.getNb_pieges() > 0) {
					myself.setNb_pieges(myself.getNb_pieges()-1);
					double x = myself.getShip().get_posX();
					double y = myself.getShip().get_posY();
					x = x + ve_radius*2 * Math.cos((Math.toRadians(myself.getShip().getAngle()) - Math.PI));
					y = y + ve_radius*2 * Math.sin((Math.toRadians(myself.getShip().getAngle()) - Math.PI));
					sendPiege(new Point(x,y));
				}else {System.out.println("No more bananas :) ");}
			}
		});

	}
	public void initializeLobby() {
		//label username
		Text lobby_username_label = new Text("Username");
		//Text Filed for username
		TextField lobby_username_field = new TextField();
		//Buttons
		Button connect = new Button("Connect");
		connect.setOnAction(e -> {
			name = lobby_username_field.getText();
			sendConnect(name);
			try {
				String server_input = inchan.readLine();
				System.out.println("repnse à la demande de connexion : "+server_input);
				String[] server_split = server_input.split("/");
				if(server_split != null) {
					switch(server_split[0]){
					case "WELCOME" :
						process_welcome(server_split);
						initializeMain();
						r = new Receive(this,inchan);
						r.start();
						primaryStage.setScene(playScene);
						break;
					case "DENIED" :
						Text t;
						if (server_split.length > 1) {
							t = new Text(server_split[1]);
							t.setFill(Color.RED);
							lobbyPane.add(t, 1, 1);
						}else{
							t = new Text("Connection denied");
							t.setFill(Color.RED);
							lobbyPane.add(t, 1, 1);
						}
						break;
					default : System.out.println("Unknown protocol");
					}
				}else {throw new IOException();}
			}catch(IOException e2) {
				e2.printStackTrace();
			}});
		//Grid Pane
		lobbyPane = new GridPane();
		lobbyPane.setMinSize(400, 200);
		lobbyPane.setPadding(new Insets(10, 10, 10, 10));
		lobbyPane.setVgap(5);
		lobbyPane.setHgap(10);
		lobbyPane.setAlignment(Pos.CENTER);
		lobbyPane.add(lobby_username_label, 0, 0);
		lobbyPane.add(lobby_username_field, 1, 0);
		lobbyPane.add(connect, 0, 1);

		//Scene
		lobbyScene = new Scene(lobbyPane);
		//Stage
		primaryStage.setTitle("SpaceRun");
		primaryStage.setScene(lobbyScene);
	}
	public static void main(String[] args) {launch(args);}

//------------------------------------------------------------------------//
//																      	//
//							COMMUNICATIONS								//
//																		//
//------------------------------------------------------------------------//
	//**************************AUX***************************************

	public void parse_status(String status) {
		if (status == "jeu") {
			isPlaying=true;
		}
		if (status == "attente") {
			isPlaying = false;
		}
	}
	public void parse_scores(String player_score_string){
		String[] player_score_string_split = player_score_string.split("\\|");
		if (player_list.isEmpty()) { // initialisation, le joueur vient de se connecter
			for(int i = 0;i<player_score_string_split.length;i++){
				String[] player_score = player_score_string_split[i].split("[:]");
				Player p = new Player(player_score[0],Integer.parseInt(player_score[1]));
				player_list.put(player_score[0], p);
				if (player_score[0].equals(name)) {
					myself = p;
				}
			}
		}else{ // mise à jour des scores
			for (int i = 0; i<player_score_string_split.length; i++) {
				String[] player_score = player_score_string_split[i].split("[:]");
				int last_score = player_list.get(player_score[0]).getScore();
				int new_score = Integer.parseInt(player_score[1]);
				if (last_score < new_score) player_list.get(player_score[0]).setNb_pieges(player_list.get(player_score[0]).getNb_pieges()+1);
				player_list.get(player_score[0]).setScore(new_score);
			}
		}
	}
	public void parse_coords(String player_coord_string){
		String[] player_coord_string_split = player_coord_string.split("\\|");
		for(String p : player_coord_string_split) {
			String[] xy = p.split(":")[1].split("[XY]|VX|VY|T");
			double x = Double.parseDouble(xy[1]);
			double y = Double.parseDouble(xy[2]);
			String name = p.split(":")[0];
			player_list.get(name).getShip().set_posX(x);
			player_list.get(name).getShip().set_posY(y);
		}
	}
	public void parse_vcoords(String player_coord_string){
		String[] player_coord_string_split = player_coord_string.split("\\|");
		for(String p : player_coord_string_split) {
			String[] xy = p.split(":")[1].split("[XY]|VX|VY|T");
			double x = Double.parseDouble(xy[1]);
			double y = Double.parseDouble(xy[2]);
			double vx = Double.parseDouble(xy[3]);
			double vy = Double.parseDouble(xy[4]);
			double t = Double.parseDouble(xy[5]);
			String name = p.split(":")[0];
			player_list.get(name).getShip().set_posX(x);
			player_list.get(name).getShip().set_posY(y);
			player_list.get(name).getShip().set_speedXY(vx, vy);
			player_list.get(name).getShip().setAngle(Math.toDegrees(t));
		}
	}
	public void parse_target(String coord_string){
		String[] pos_target = coord_string.split("[XY]");
		Point t = new Point(Double.parseDouble(pos_target[1]),Double.parseDouble(pos_target[2]));
		targets_list.add(t);
	}
	public void parse_co_targets(String co_targets) {
		String[] pos_targets = co_targets.split("\\|");
		for (String c : pos_targets) {
			String[] xy = c.split("[XY]");
			Point t = new Point(Double.parseDouble(xy[1]),Double.parseDouble(xy[2]));
			targets_list.add(t);
		}
		System.out.println("La taille de la liste targets = "+ targets_list.size());
	}
	public void parse_message_public(String reception) {
		Platform.runLater(new Runnable() {
            @Override public void run() {
            	received.getChildren().add(new Text(reception+"\n"));
            }
        });
	}
	public void parse_message_public(String reception,String from) {
		Platform.runLater(new Runnable() {
            @Override public void run() {
            	received.getChildren().add(new Text(from+">"+reception+"\n"));
            }
        });
	}
	public void parse_message_prive(String reception,String user) {
		Text t = new Text("DM:"+user+">"+reception+"\n");
		t.setFill(Color.DARKORANGE);
		Platform.runLater(()->received.getChildren().add(t));

	}
	public void parse_obstacles(String obstacles) {
		String[] stringListObs = obstacles.split("\\|");
		ArrayList<Point> tmp = new ArrayList<>();
		for (String s : stringListObs) {
			String[] xy = s.split("[XY]");
			double x = Double.parseDouble(xy[1]);
			double y = Double.parseDouble(xy[2]);
			tmp.add(new Point(x, y));
		}
		obstacles_list = tmp;
	}
	public void parse_pieges(String pieges) {
		String[] stringListPieges = pieges.split("\\|");
		pieges_list.clear();
		for(String s : stringListPieges) {
			String[] xy = s.split("[XY]");
			double x = Double.parseDouble(xy[1]);
			double y = Double.parseDouble(xy[2]);
			pieges_list.add(new Point(x, y));
		}
	}

//****************************PROCESS PROTOCOLES*********************
	//RECEIVE
	public void process_welcome(String[] server_input){
		parse_status(server_input[1]);
		parse_scores(server_input[2]);
		parse_target(server_input[3]);
		parse_obstacles(server_input[4]);
		if(server_input.length == 6) parse_co_targets(server_input[5]);
		Platform.runLater(new Runnable() {
            @Override public void run() {
            	Text t = new Text("Welcome to you dear "+name+" ! :)\n");
            	t.setFill(Color.TOMATO);
            	received.getChildren().add(t);
            }
        });
	}
	public void process_newplayer(String new_user){
		System.out.println("newplayer : "+new_user);
		player_list.put(new_user,new Player(new_user,0));
		Platform.runLater(new Runnable() {
            @Override public void run() {
            	Text t = new Text(new_user+" has joined the party !\n");
            	t.setFill(Color.TOMATO);
            	received.getChildren().add(t);
            }
        });
	}
	public void process_denied(String error){
		System.out.println("Error : DENIED/"+error);
	}
	public void process_playerleft(String name){
		System.out.println("playerleft : "+name);
		player_list.remove(name);
		Platform.runLater(new Runnable() {
            @Override public void run() {
            	Text t = new Text(name+" has left the party !\n");
            	t.setFill(Color.TOMATO);
            	received.getChildren().add(t);
            }
        });
	}
	public void process_session(String coords,String coord,String coords_obs){
		targets_list.clear();
		pieges_list.clear();
		updateMyscore();
		updateListPlayer();
		parse_target(coord);
		parse_coords(coords);
		parse_obstacles(coords_obs);
		isPlaying = true;
		serverTickrateTask = new SendNewComTask(this);
		serverTickrateTimer.scheduleAtFixedRate(serverTickrateTask,new Date(),server_tickrate);
	}
	public void process_session(String coords,String coord,String coords_obs,String co_targets) {
		targets_list.clear();
		pieges_list.clear();
		updateMyscore();
		updateListPlayer();
		parse_target(coord);
		parse_coords(coords);
		parse_obstacles(coords_obs);
		parse_co_targets(co_targets);
		isPlaying = true;
		serverTickrateTask = new SendNewComTask(this);
		serverTickrateTimer.scheduleAtFixedRate(serverTickrateTask,new Date(),server_tickrate);
	}
	public void process_winner(String scores){
		parse_scores(scores);
		updateMyscore();
		Platform.runLater(new Runnable() {
            @Override public void run() {
            	updateListPlayer();
            }
        });
		next_target = 0;
		isPlaying = false;
		//targets_list.clear();
		serverTickrateTask.cancel();
		/**  AFFICHER LE WINNER DANS LE CHAT **/
		Platform.runLater(new Runnable() {
            @Override public void run() {
            	Text t = new Text("\n\n"
            			+ "--------------------WINNER----------------\n"
            			+ "|  	        			  				|\n"
            			+ "|    	 	  "+whoIsWinner()+"   	    |\n"
            			+ "|  	   					       			|\n"
            			+ "------------------------------------------\n\n");
            	t.setFill(Color.TOMATO);
            	received.getChildren().add(t);
            }
        });
		
	}
	public void process_tick(String vcoords){
		parse_vcoords(vcoords);
		pieges_list.clear();
	}
	public void process_tick(String vcoords,String pieges){
		parse_vcoords(vcoords);
		parse_pieges(pieges);
	}
	public void process_newobj(String coord,String scores){
		targets_list.clear();
		next_target = 0;
		parse_target(coord);
		parse_scores(scores);
		updateMyscore();
		updateListPlayer();
		System.out.println("new_obj : " + coord);
	}
	public void process_newobj(String co_t, String scores, String co_targets) {
		targets_list.clear();
		next_target = 0;
		parse_target(co_t);
		parse_scores(scores);
		parse_co_targets(co_targets);
		updateMyscore();
		updateListPlayer();
		System.out.println("new_obj multiple");
	}
	public void process_reception(String message) {
		System.out.println("reception : "+message);
		parse_message_public(message);

	}
	public void process_reception(String message,String from) {
		System.out.println("reception : "+message+" from"+from);
		parse_message_public(message,from);
	}
	public void process_preception(String message,String user) {
		System.out.println("reception privee : "+user+"/"+message);
		parse_message_prive(message,user);
	}
	public void process_next(String index) {
		System.out.println(index);
		next_target = Integer.parseInt(index);
	}
	
	/**************************SEND FUNCTIONS**************************/
	public void sendConnect (String username) {
		outchan.println("CONNECT/"+name+"/");
		outchan.flush();
	}
	public void sendExit (String username) {
		outchan.println("EXIT/"+name+"/");
		outchan.flush();
	}
	public void sendNewpos (double x, double y) {
		outchan.println("NEWPOS/X"+x+"Y"+y+"/");
		outchan.flush();
	}
	@SuppressWarnings("unchecked")
	public void sendNewCom () {
		double a = 0.;//angle en radian
		int t = 0;//poussée
		ArrayList<Commands> temp = (ArrayList<Commands>) cumulCmds.clone();
		cumulCmds.clear();
		for(Commands c : temp) {
			if(c == Commands.thrust) {
				t +=1;
			}
			if(c == Commands.clock) {
				a = (a-myself.getShip().turnit)%360;
			}
			if(c == Commands.anticlock) {
				a = (a+myself.getShip().turnit)%360;
			}
		}
		outchan.println("NEWCOM/A"+a+"T"+t+"/");
		outchan.flush();
	}
	public void sendEnvoi(String message,String myself) {
		outchan.println("ENVOI/"+message+"/"+myself+"/");
		outchan.flush();
	}
	public void sendPEnvoi(String user,String message) {
		outchan.println("PENVOI/"+user+"/"+message+"/");
		outchan.flush();
	}
	public void sendPiege(Point pos) {
		outchan.println("NEWPIEGE/X"+pos.getX()+"Y"+pos.getY()+"/");
		outchan.flush();
	}
}
