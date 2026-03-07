package com.nelson.gitbridge;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.geometry.Insets;
import javafx.util.Duration;

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
    VBox VBHistory = new VBox();
    HBox HBRight = new HBox();
    VBox VBDiff = new VBox();
    private String repositoryPath;
    TextField TFPath = new TextField();
    TableView<ChangeItem> TVChanges = new TableView<>();
    TableColumn<ChangeItem, String> colFile = new TableColumn<>();
    Label LBOutgoing = new Label();
    Label LBIncoming = new Label();
    Label LBRepoStatus = new Label("");
    TextArea TALog = new TextArea();
    TextField TFCommitTitle = new TextField();
    TextArea TADescription = new TextArea();
    Button BTCommitPush = new Button();
    TextArea TAHistory = new TextArea();
    Label LBHistory = new Label();
    TextArea TADiff = new TextArea();
    Label LBDiff = new Label();
    Label LBChanges = new Label();
    SplitPane splitCenter = new SplitPane();
    SplitPane splitRight = new SplitPane();

    @Override
    public void start(Stage primaryStage) {
        configureLayout();

        buildTop();
        buildLeft();
        buildRight();

        assembleLayout();

        Scene scene = new Scene(BPMain, 750, 800);

        scene.getStylesheets().add(getClass().getResource("/styless.css").toExternalForm());

        setRepoStatus("❓ Repo Not found", "repo-neutral");

        styless();
        setDisableInit(true);

        primaryStage.setTitle("GitBridge");
        primaryStage.setScene(scene);
        primaryStage.show();

        Timeline autoRefresh = new Timeline(
                new KeyFrame(Duration.seconds(3), e -> {
                    if (repositoryPath != null) {
                        refreshChanges();
                    }
                })
        );

        autoRefresh.setCycleCount(Timeline.INDEFINITE);
        autoRefresh.play();
    }

    public class GitService {

        public static String runGitCommand(String repoPath, String... command) throws Exception {

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(new File(repoPath));

            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
            );

            StringBuilder output = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            process.waitFor();

            return output.toString();
        }

        public void runGitCommandLive(String repoPath, Runnable onFinish, String... command) {

            Task<Void> task = new Task<>() {

                @Override
                protected Void call() throws Exception {

                    ProcessBuilder pb = new ProcessBuilder(command);
                    pb.directory(new File(repoPath));
                    pb.redirectErrorStream(true);

                    Process process = pb.start();

                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(process.getInputStream())
                    );

                    String line;

                    while ((line = reader.readLine()) != null) {

                        String finalLine = line;

                        Platform.runLater(() -> {
                            log(finalLine + "\n");
                        });
                    }

                    process.waitFor();

                    return null;
                }
            };

            task.setOnSucceeded(e -> {
                if (onFinish != null) {
                    onFinish.run();
                }
            });

            Thread thread = new Thread(task);
            thread.setDaemon(true);
            thread.start();
        }
    }

    private void setDisableInit(boolean estado) {
        VBLeft.setDisable(estado);
        VBRight.setDisable(estado);
        splitCenter.setDisable(estado);
    }

    private void pullChanges() {
        TALog.clear();

        GitService service = new GitService();

        service.runGitCommandLive(
                repositoryPath,
                () -> refreshIncomingOutgoing(),
                "git",
                "pull",
                "--rebase"
        );
        refreshCommitHistory();
    }

    private void commitPush() {

        BTCommitPush.setDisable(true);

        TALog.clear();

        try {

            String status = GitService.runGitCommand(
                    repositoryPath,
                    "git",
                    "status",
                    "--porcelain"
            );

            if (status == null || status.isBlank()) {

                log("No changes to commit. Pushing existing commits...\n");

                new GitService().runGitCommandLive(
                        repositoryPath,
                        () -> refreshIncomingOutgoing(),
                        "git",
                        "push"
                );

                return;
            }

            String title = TFCommitTitle.getText().trim();

            if (title == null || title.isBlank()) {
                log("Commit message is empty\n");
                return;
            }

            log("Adding changes...\n");
            GitService.runGitCommand(repositoryPath, "git", "add", ".");

            log("Creating commit...\n");

            String description = TADescription.getText().trim();

            String commitOutput;

            if (description == null || description.isBlank()) {

                commitOutput = GitService.runGitCommand(
                        repositoryPath,
                        "git",
                        "commit",
                        "-m",
                        title
                );

            } else {

                commitOutput = GitService.runGitCommand(
                        repositoryPath,
                        "git",
                        "commit",
                        "-m",
                        title,
                        "-m",
                        description
                );
            }

            log(commitOutput);

            log("Pushing to remote...\n");

            String pushOutput = GitService.runGitCommand(
                    repositoryPath,
                    "git",
                    "push"
            );

            log(pushOutput);

            if (pushOutput.contains("non-fast-forward") || pushOutput.contains("failed to push")) {

                log("Remote has new commits. Pulling with rebase...\n");

                String pullOutput = GitService.runGitCommand(
                        repositoryPath,
                        "git",
                        "pull",
                        "--rebase"
                );

                log(pullOutput);

                log("Retrying push...\n");

                new GitService().runGitCommandLive(
                        repositoryPath,
                        () -> refreshIncomingOutgoing(),
                        "git",
                        "push"
                );

            } else {
                refreshIncomingOutgoing();
            }

            TFCommitTitle.setText("");
            TADescription.setText("");
            BTCommitPush.setDisable(false);

        } catch (Exception e) {

            log("System error: " + e.getMessage() + "\n");
        }
        refreshCommitHistory();
    }

    private void log(String text) {
        TALog.appendText(text);
        TALog.positionCaret(TALog.getLength());
    }

    private void refreshCommitHistory() {

        try {

            String logOutput = GitService.runGitCommand(
                    repositoryPath,
                    "git",
                    "log",
                    "--pretty=format:%h | %ad | %an | %s",
                    "--date=short",
                    "-n",
                    "20"
            );

            TAHistory.clear();
            TAHistory.appendText(logOutput);

            TAHistory.positionCaret(0);

        } catch (Exception e) {

            TAHistory.setText("Error loading history");
        }
    }

    private void refreshIncomingOutgoing() {
         Task<String> task = new Task<>() {

            @Override
            protected String call() throws Exception {

                GitService.runGitCommand(
                        repositoryPath,
                        "git",
                        "fetch",
                        "--prune"
                );

                try {
                    GitService.runGitCommand(
                            repositoryPath,
                            "git",
                            "rev-parse",
                            "--abbrev-ref",
                            "--symbolic-full-name",
                            "@{u}"
                    );
                } catch (Exception e) {
                    return "NO_UPSTREAM";
                }

                return GitService.runGitCommand(
                        repositoryPath,
                        "git",
                        "rev-list",
                        "--left-right",
                        "--count",
                        "HEAD...@{upstream}"
                );
            }
        };

        task.setOnSucceeded(e -> {
            String output = task.getValue();

            if (output != null) {
                String[] parts = output.trim().split("\\s+");

                if (parts.length >= 2) {
                    LBOutgoing.setText("Push: " + parts[0] + " ▲");
                    LBIncoming.setText("Pull: " + parts[1] + " ▼");
                }
            }
        }
        );
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private void refreshChanges() {
        Task<String> task = new Task<>() {

            @Override
            protected String call() throws Exception {

                return GitService.runGitCommand(
                        repositoryPath,
                        "git",
                        "status",
                        "--porcelain=v1",
                        "-u"
                );
            }
        };

        task.setOnSucceeded(e -> {

            String output = task.getValue();

            String[] parts = output.replace("gitbridge-app/", "").split("\\n");

            TVChanges.getItems().clear();

            for (String line : parts) {

                String badge = line.substring(0, 2).trim();
                String file = line.substring(3).trim();

                TVChanges.getItems().add(new ChangeItem(badge, file));
            }

            autoResizeFileColumn();
            refreshIncomingOutgoing();
            LBChanges.setText("Changes: (" + TVChanges.getItems().size() + ")");
        }
        );
        new Thread(task).start();
    }

    private void configureLayout() {
        HBPathContainer.setAlignment(Pos.CENTER_LEFT);
        HBPathContainer.setSpacing(8);

        splitCenter.setPadding(new Insets(1, 9, 9, 9));
        splitCenter.setDividerPositions(0.45);

        HBTop.setSpacing(9);
        HBTop.setPadding(new Insets(9));

        HBLeft.setSpacing(9);
        HBRight.setSpacing(9);

        VBLeft.setSpacing(9);
        VBRight.setSpacing(9);

        VBChanges.setSpacing(0);
    }

    private void assembleLayout() {

        HBTop.getChildren().add(HBPathContainer);

        splitCenter.getItems().addAll(VBLeft, VBRight);
        BPMain.setCenter(splitCenter);

        VBLeft.setPrefWidth(320);
        VBLeft.setMaxWidth(500);
        HBox.setHgrow(VBLeft, Priority.NEVER);

        HBox.setHgrow(VBRight, Priority.ALWAYS);
        VBRight.setMaxWidth(Double.MAX_VALUE);
    }

    private void styless() {
        HBPathContainer.getStyleClass().add("path-container");
        LBRepoStatus.getStyleClass().add("repo-status");
        VBChanges.getStyleClass().add("VBChanges");
        VBLog.getStyleClass().add("VBLog");
        VBHistory.getStyleClass().add("VBLog");
        VBDiff.getStyleClass().add("VBLog");
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

        HBPathContainer.getChildren().addAll(TFPath, LBRepoStatus);

        HBox.setHgrow(HBPathContainer, Priority.ALWAYS);
        HBPathContainer.setMaxWidth(Double.MAX_VALUE);

        BPMain.setTop(HBTop);
    }

    private boolean isValidGitRepository(File directory) {
        File gitFolder = new File(directory, ".git");
        return gitFolder.exists() && gitFolder.isDirectory();
    }

    private void selectRepository() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Git Repository");

        File selectedDirectory = directoryChooser.showDialog(BPMain.getScene().getWindow());

        if (selectedDirectory != null) {

            if (isValidGitRepository(selectedDirectory)) {
                repositoryPath = selectedDirectory.getAbsolutePath();

                TFPath.setText(repositoryPath);



                refreshChanges();

                setRepoStatus("✔ Repo OK", "repo-ok");

                setDisableInit(false);

                refreshCommitHistory();

            } else {
                setRepoStatus("✖ Repo Error", "repo-invalid");
            }
        }
    }

        private void setRepoStatus(String text, String styleClass) {
        LBRepoStatus.setText(text);

            LBRepoStatus.getStyleClass().removeAll("repo-ok", "repo-invalid", "repo-neutral");

            LBRepoStatus.getStyleClass().add(styleClass);
    }

    private void buildLeft() {
        VBLeft.getChildren().add(HBLeft);
        VBLeft.getChildren().add(VBChanges);

        Button BTRefresh = new Button();
        BTRefresh.setText("Refresh");
        BTRefresh.setOnAction(e -> {
            refreshChanges();
            refreshCommitHistory();
        });
        BTRefresh.getStyleClass().add("secondary-button");
        HBLeft.getChildren().add(BTRefresh);

        LBOutgoing.setText("Push: 0 ▲");
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
        VBHistory.setAlignment(Pos.TOP_CENTER);
        VBDiff.setAlignment(Pos.TOP_CENTER);

        VBChanges.setMaxWidth(Double.MAX_VALUE);

        Label LBNewCommit = new Label();
        LBNewCommit.setText(" New Commit");
        LBNewCommit.getStyleClass().add("new-commit");
        VBLeft.getChildren().add(LBNewCommit);

        TFCommitTitle.setPromptText("Commit title");
        TFCommitTitle.setStyle("-fx-font-size: 14px;");
        TFCommitTitle.getStyleClass().add("tf-general");
        VBLeft.getChildren().add(TFCommitTitle);

        TADescription.setPromptText("Description");
        TADescription.setPrefRowCount(15);
        TADescription.setMaxWidth(Double.MAX_VALUE);
        TADescription.setMinHeight(75);
        TADescription.setMaxHeight(150);
        VBox.setVgrow(TADescription, Priority.ALWAYS);
        TADescription.setWrapText(true);
        TADescription.setStyle("-fx-font-size: 14px;");
        TADescription.getStyleClass().add("tf-general");
        VBLeft.getChildren().add(TADescription);

        BTCommitPush.setText("Commit & Push");
        BTCommitPush.setMaxWidth(Double.MAX_VALUE);
        BTCommitPush.setMaxWidth(Double.MAX_VALUE);
        BTCommitPush.getStyleClass().add("primary-button");
        BTCommitPush.setOnAction(e -> commitPush());
        VBLeft.getChildren().add(BTCommitPush);
    }

    private void buildTableChanges() {
        LBChanges.setText("Changes");
        LBChanges.getStyleClass().add("title-panel");

        VBox.setMargin(LBChanges, new Insets(8, 0, 0, 0));

        TableColumn<ChangeItem, String> colStatus = new TableColumn<>();

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

        colStatus.setPrefWidth(40);
        colStatus.setMinWidth(40);
        colStatus.setMaxWidth(40);
        colStatus.setResizable(false);

        colFile.setResizable(false);
        colFile.setMinWidth(200);
        TVChanges.setSelectionModel(null);
        colFile.setPrefWidth(400);
        colFile.setMinWidth(200);
        colFile.setResizable(true);

        VBox.setVgrow(VBChanges, Priority.ALWAYS);
        VBox.setVgrow(TVChanges, Priority.ALWAYS);
        TVChanges.setMaxHeight(Double.MAX_VALUE);
        VBChanges.getChildren().addAll(LBChanges, TVChanges);
        TVChanges.setMaxWidth(Double.MAX_VALUE);
        TVChanges.getStyleClass().add("table-view");
        TVChanges.getStyleClass().add("tv-changes");
    }

    private void autoResizeFileColumn() {

        double max = 0;

        for (ChangeItem item : TVChanges.getItems()) {

            Text text = new Text(item.getFileName());
            double width = text.getLayoutBounds().getWidth();

            if (width > max) {
                max = width;
            }
        }
        colFile.setPrefWidth(max + 40);
    }

    private void buildRight() {

        VBox.setVgrow(VBLog, Priority.ALWAYS);
        VBox.setVgrow(VBHistory, Priority.ALWAYS);

        Button BTPull = new Button();
        BTPull.setText("Pull changes");
        BTPull.setOnAction(e -> pullChanges());
        BTPull.getStyleClass().add("secondary-button");
        HBRight.getChildren().add(BTPull);

        LBIncoming.setText("Pull: 0 ▼");
        LBIncoming.setStyle("-fx-font-size: 14px;");
        HBRight.getChildren().add(LBIncoming);

        Label LBLog = new Label();
        LBLog.setText("Log");
        LBLog.setStyle("-fx-font-size: 14px;");
        VBox.setMargin(LBLog, new Insets(8, 0, 0, 0));
        LBLog.getStyleClass().add("title-panel");

        TALog.setEditable(false);
        TALog.setWrapText(true);
        VBox.setVgrow(TALog, Priority.ALWAYS);
        TALog.setMaxHeight(Double.MAX_VALUE);
        TALog.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 14px;");
        TALog.getStyleClass().add("console-log");
        VBLog.getChildren().addAll(LBLog, TALog);
        VBHistory.getChildren().addAll(LBHistory, TAHistory);
        VBRight.setMaxWidth(Double.MAX_VALUE);

        LBHistory.setText("Commit History");
        LBHistory.setStyle("-fx-font-size: 14px;");
        VBox.setMargin(LBHistory, new Insets(8, 0, 0, 0));
        LBHistory.getStyleClass().add("title-panel");

        TAHistory.setEditable(false);
        TAHistory.setWrapText(false);
        VBox.setVgrow(TAHistory, Priority.ALWAYS);
        TAHistory.setMaxHeight(Double.MAX_VALUE);
        TAHistory.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 14px;");
        TAHistory.getStyleClass().add("console-log");

        LBDiff.setText("Diff");
        LBDiff.setStyle("-fx-font-size: 14px;");
        VBox.setMargin(LBDiff, new Insets(8, 0, 0, 0));
        LBDiff.getStyleClass().add("title-panel");

        TADiff.setEditable(false);
        TADiff.setWrapText(false);
        VBox.setVgrow(TADiff, Priority.ALWAYS);
        TADiff.setMaxHeight(Double.MAX_VALUE);
        TADiff.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 14px;");
        TADiff.getStyleClass().add("console-log");

        VBDiff.getChildren().addAll(LBDiff, TADiff);

        splitRight.setOrientation(Orientation.VERTICAL);

        splitRight.getItems().addAll(

                VBDiff,
                VBLog,
                VBHistory
        );

        splitRight.setDividerPositions(0.3, 0.6);

        VBox.setVgrow(splitRight, Priority.ALWAYS);
        splitRight.setMaxHeight(Double.MAX_VALUE);

        VBRight.getChildren().addAll(
                HBRight,
                splitRight
        );

        splitCenter.setOrientation(Orientation.HORIZONTAL);

        VBox.setVgrow(splitCenter, Priority.ALWAYS);
        splitCenter.setMaxHeight(Double.MAX_VALUE);
        splitCenter.getStyleClass().add("split-pane");

        splitRight.getStyleClass().add("split-pane");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
