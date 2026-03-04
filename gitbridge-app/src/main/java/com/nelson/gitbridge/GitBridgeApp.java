package com.nelson.gitbridge;

import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.geometry.Insets;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

public class GitBridgeApp extends Application {

    BorderPane BPMain = new BorderPane();
    HBox HBFather = new HBox();
    HBox HBTop = new HBox();
    HBox HBPathContainer = new HBox();
    VBox VBLeft = new VBox();
    VBox VBRight = new VBox();
    HBox HBLeft = new HBox();
    VBox VBChanges = new VBox();
    VBox VBLog = new VBox();
    HBox HBRight = new HBox();
    private String repositoryPath;
    TextField TFPath = new TextField();
    ComboBox CBRaiz = new ComboBox();
    TableView<ChangeItem> TVChanges = new TableView<>();

    @Override
    public void start(Stage primaryStage) {
        configureLayout();

        buildTop();
        buildLeft();
        buildRight();

        assembleLayout();

        Scene scene = new Scene(BPMain, 750, 600);

        scene.getStylesheets().add(getClass().getResource("/styless.css").toExternalForm());

        styless();

        primaryStage.setTitle("GitBridge");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public class GitService {

        public static String runGitCommand(String repoPath, String... command) throws Exception {

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(new File(repoPath));

            Process process = processBuilder.start();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));

            StringBuilder output = new StringBuilder();

            String line;

            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            process.waitFor();

            return output.toString();
        }
    }

    private void refreshChanges() {
        Task<String> task = new Task<>() {

            @Override
            protected String call() throws Exception {

                return GitService.runGitCommand(
                        repositoryPath,
                        "git",
                        "status",
                        "--porcelain"
                );
            }
        };

        task.setOnSucceeded(e -> {

            String output = task.getValue();

            String[] parts = output.replace("gitbridge-app/", "").trim().split("\\n");

            TVChanges.getItems().clear();

            for (int i = 0; i < parts.length; i++) {

                String[] badgeFile = parts[i].trim().split(" ");
                String badge = badgeFile[0];
                String file = badgeFile[1];

                TVChanges.getItems().addAll(new ChangeItem(badge, file));



            }

        });

        new Thread(task).start();
    }

    private void configureLayout() {
        HBPathContainer.setAlignment(Pos.CENTER_LEFT);
        HBPathContainer.setSpacing(8);

        HBFather.setSpacing(9);
        HBFather.setPadding(new Insets(1, 9, 9, 9));
        HBFather.setFillHeight(true);

        HBTop.setSpacing(9);
        HBTop.setPadding(new Insets(9));

        HBLeft.setSpacing(9);
        HBRight.setSpacing(9);

        VBLeft.setSpacing(9);
        VBRight.setSpacing(9);

        VBChanges.setSpacing(0);
    }

    private void assembleLayout() {
        VBRight.getChildren().addAll(HBRight, VBLog);
        HBTop.getChildren().add(HBPathContainer);
        HBFather.getChildren().addAll(VBLeft, VBRight);

        VBLeft.setMaxWidth(Double.MAX_VALUE);

        BPMain.setCenter(HBFather);
    }

    private void styless() {
        HBPathContainer.getStyleClass().add("path-container");
        VBChanges.getStyleClass().add("VBChanges");
        VBLog.getStyleClass().add("VBLog");
    }

    private void buildTop() {
        Button BTSelectRepository = new Button();
        BTSelectRepository.setText("Select Repository");
        BTSelectRepository.setOnAction(e -> System.out.println("¡Botón presionado!"));
        BTSelectRepository.getStyleClass().add("secondary-button");
        BTSelectRepository.setOnAction(e -> selectRepository());
        HBTop.getChildren().add(BTSelectRepository);

        HBox.setHgrow(TFPath, Priority.ALWAYS);
        TFPath.setPromptText("Path");
        TFPath.setEditable(false);
        TFPath.setMaxWidth(Double.MAX_VALUE);
        TFPath.getStyleClass().add("path-field");
        TFPath.getStyleClass().add("tf-path");

        Label LBRepoStatus = new Label("✔ Repo OK");
        LBRepoStatus.getStyleClass().add("repo-status");

        HBPathContainer.getChildren().addAll(TFPath, LBRepoStatus);

        HBox.setHgrow(HBPathContainer, Priority.ALWAYS);
        HBPathContainer.setMaxWidth(Double.MAX_VALUE);

        VBLeft.prefWidthProperty().bind(HBFather.widthProperty().multiply(0.460));
        VBRight.prefWidthProperty().bind(HBFather.widthProperty().multiply(0.540));

        BPMain.setTop(HBTop);
    }

    private void selectRepository() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Git Repository");

        File selectedDirectory = directoryChooser.showDialog(BPMain.getScene().getWindow());

        if (selectedDirectory != null) {

            repositoryPath = selectedDirectory.getAbsolutePath();

            TFPath.setText(repositoryPath);

            loadRootFolders();

            System.out.println(repositoryPath);
        }
    }

    private void loadRootFolders() {

        File repo = new File(repositoryPath);

        File[] files = repo.listFiles(File::isDirectory);

        CBRaiz.getItems().clear();

        for (File file : files) {

            if (!file.getName().equals(".git")) {
                CBRaiz.getItems().add(file.getName());
            }
        }
    }

    private void buildLeft() {
        VBLeft.getChildren().add(HBLeft);
        VBLeft.getChildren().add(VBChanges);



        CBRaiz.getStyleClass().add("cb-raiz");
        HBLeft.getChildren().add(CBRaiz);

        Button BTRefresh = new Button();
        BTRefresh.setText("Refresh");
        BTRefresh.setOnAction(e -> refreshChanges());
        BTRefresh.getStyleClass().add("secondary-button");
        HBLeft.getChildren().add(BTRefresh);

        Label LBOutgoing = new Label();
        LBOutgoing.setText("Outgoing: ");
        LBOutgoing.setStyle("-fx-font-size: 14px;");
        HBLeft.getChildren().add(LBOutgoing);

        buildTableChanges();

        VBox.setVgrow(VBLeft, Priority.ALWAYS);
        VBox.setVgrow(VBRight, Priority.ALWAYS);

        VBLeft.setMaxHeight(Double.MAX_VALUE);
        VBRight.setMaxHeight(Double.MAX_VALUE);

        HBLeft.setAlignment(Pos.CENTER_LEFT);
        HBRight.setAlignment(Pos.CENTER_LEFT);

        VBox.setVgrow(VBChanges, Priority.ALWAYS);
        VBox.setVgrow(VBLog, Priority.ALWAYS);

        VBChanges.setAlignment(Pos.TOP_CENTER);
        VBLog.setAlignment(Pos.TOP_CENTER);

        VBChanges.setMaxWidth(Double.MAX_VALUE);

        Label LBNewCommit = new Label();
        LBNewCommit.setText(" New Commit");
        LBNewCommit.getStyleClass().add("new-commit");
        VBLeft.getChildren().add(LBNewCommit);

        TextField TFCommitTitle = new TextField();
        TFCommitTitle.setPromptText("Commit title");
        TFCommitTitle.setStyle("-fx-font-size: 14px;");
        TFCommitTitle.getStyleClass().add("tf-general");
        VBLeft.getChildren().add(TFCommitTitle);

        TextArea TADescription = new TextArea();
        TADescription.setPromptText("Description");
        TADescription.setPrefRowCount(15);
        TADescription.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(TADescription, Priority.ALWAYS);
        TADescription.setWrapText(true);
        TADescription.setStyle("-fx-font-size: 14px;");
        TADescription.getStyleClass().add("tf-general");
        VBLeft.getChildren().add(TADescription);

        Button BTCommitPush = new Button();
        BTCommitPush.setText("Commit & Push");
        BTCommitPush.setMaxWidth(Double.MAX_VALUE);
        BTCommitPush.setMaxWidth(Double.MAX_VALUE);
        BTCommitPush.getStyleClass().add("primary-button");
        VBLeft.getChildren().add(BTCommitPush);
    }

    private void buildTableChanges() {
        Label LBChanges = new Label();
        LBChanges.setText("Changes");
        LBChanges.getStyleClass().add("title-panel");

        VBox.setMargin(LBChanges, new Insets(8, 0, 0, 0));



        TableColumn<ChangeItem, String> colStatus = new TableColumn<>();
        TableColumn<ChangeItem, String> colFile = new TableColumn<>();

        colStatus.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getStatus()));

        colFile.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getFileName()));

        colStatus.setCellFactory(column -> new TableCell<ChangeItem, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setGraphic(null);
                } else {

                    Label badge = new Label(item);
                    badge.setAlignment(Pos.CENTER);
                    badge.setMinSize(24, 24);
                    badge.setMaxSize(24, 24);
                    badge.getStyleClass().add("status-badge");

                    switch (item) {
                        case "M" -> badge.getStyleClass().add("status-modified");
                        case "A" -> badge.getStyleClass().add("status-added");
                        case "D" -> badge.getStyleClass().add("status-deleted");
                    }

                    setGraphic(badge);
                    setText(null);
                    setAlignment(Pos.CENTER);
                    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                }
            }
        });

        TVChanges.getColumns().addAll(colStatus, colFile);

        TVChanges.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        colStatus.setPrefWidth(40);
        colStatus.setMinWidth(40);
        colStatus.setMaxWidth(40);
        colStatus.setResizable(false);

        colFile.setResizable(false);
        TVChanges.setSelectionModel(null);

        //TVChanges.getItems().addAll(
        //        new ChangeItem("M", "src/main.cs"),
        //        new ChangeItem("A", "README.md"),
        //        new ChangeItem("D", "old_file.txt"),
        //        new ChangeItem("M", "src/main.cs"),
        //        new ChangeItem("A", "README.md"),
        //        new ChangeItem("D", "old_file.txt"),
        //        new ChangeItem("M", "src/main.cs"),
        //        new ChangeItem("A", "README.md"),
        //        new ChangeItem("D", "old_file.txt"),
        //        new ChangeItem("M", "src/main.cs"),
        //        new ChangeItem("A", "README.md"),
        //        new ChangeItem("D", "old_file.txt")
        //);

        VBox.setVgrow(VBChanges, Priority.ALWAYS);
        VBox.setVgrow(TVChanges, Priority.ALWAYS);
        TVChanges.setMaxHeight(Double.MAX_VALUE);
        VBChanges.getChildren().addAll(LBChanges, TVChanges);
        TVChanges.setMaxWidth(Double.MAX_VALUE);
        TVChanges.getStyleClass().add("table-view");
        TVChanges.getStyleClass().add("tv-changes");
    }

    private void buildRight() {
        Button BTPull = new Button();
        BTPull.setText("Pull changes");
        BTPull.setOnAction(e -> System.out.println("¡Botón presionado!"));
        BTPull.getStyleClass().add("secondary-button");
        HBRight.getChildren().add(BTPull);

        Label LBIncoming = new Label();
        LBIncoming.setText("Incoming: ");
        LBIncoming.setStyle("-fx-font-size: 14px;");
        HBRight.getChildren().add(LBIncoming);

        Label LBLog = new Label();
        LBLog.setText("Log");
        LBLog.setStyle("-fx-font-size: 14px;");
        VBox.setMargin(LBLog, new Insets(8, 0, 0, 0));
        LBLog.getStyleClass().add("title-panel");

        TextArea TALog = new TextArea();
        TALog.setEditable(false);
        TALog.setWrapText(true);
        VBox.setVgrow(TALog, Priority.ALWAYS);
        TALog.setMaxHeight(Double.MAX_VALUE);
        TALog.setStyle("-fx-font-size: 14px;");
        TALog.getStyleClass().add("console-log");
        VBLog.getChildren().addAll(LBLog, TALog);
        VBRight.setMaxWidth(Double.MAX_VALUE);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
