package com.xin.controller;

import com.xin.ZkConfService;
import com.xin.util.FuzzyMatchUtils;
import com.xin.util.StringUtil;
import com.xin.view.ZkExceptionDialog;
import com.xin.view.conf.SearchFilterObservalbeList;
import com.xin.view.conf.ZkConfListView;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.controlsfx.control.HyperlinkLabel;

import java.awt.*;
import java.io.File;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ResourceBundle;

/**
 * @author 497668869@qq.com
 * @since 1.0
 */
@Slf4j
public class RootController implements Initializable {

    public MenuItem exitBtn;
    public MenuItem newConnBtn;
    /**
     * 配置列表
     */
    public ZkConfListView zkConfListView;
    public TextField filterTextField;
    public TabPane connectTabPane;
    public HyperlinkLabel welcomeInfo;
    public SplitPane splitPane;
    public HBox leftWrapper;
    public BorderPane leftPane;
    public Button toggleLeftPaneBtn;

    private boolean leftPaneCollapsed = false;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        try {
            welcomeInfo.setText(IOUtils.toString(RootController.class.getClassLoader()
                                                                     .getResource("welcomeInfo.txt")
                                                                     .toURI(), StandardCharsets.UTF_8));
            welcomeInfo.setOnAction(event -> {
                Hyperlink link = (Hyperlink) event.getSource();
                final String str = link.getText();
                try {
                    Desktop.getDesktop()
                           .browse(new URI(str));
                } catch (Exception e) {
                    log.error("打开github网页失败", e);
                }
            });

            zkConfListView.installConnectTrigger(connectTabPane);

            installConfSearchFilter();

        } catch (Exception e) {
            log.info("初始化异常", e);
            new ZkExceptionDialog("初始化异常", e)
                    .showUi();
        }

    }

    public void exitBtnAction() {
        Platform.exit();
    }

    private Tab logTab;

    public void openLogFiles(ActionEvent actionEvent) {
        if (logTab != null && connectTabPane.getTabs().contains(logTab)) {
            connectTabPane.getSelectionModel().select(logTab);
            return;
        }
        File file = new File("logs/zookeeper-visualizer.log");
        try {
            String logContent;
            if (file.exists()) {
                logContent = IOUtils.toString(file.toURI(), StandardCharsets.UTF_8);
            } else {
                logContent = "日志文件不存在: " + file.getAbsolutePath();
            }
            TextArea textArea = new TextArea(logContent);
            textArea.setEditable(false);
            textArea.setWrapText(false);
            textArea.setStyle("-fx-font-family: monospace; -fx-font-size: 12px;");
            logTab = new Tab("查看日志");
            logTab.setContent(textArea);
            logTab.setClosable(true);
            connectTabPane.getTabs().add(logTab);
            connectTabPane.getSelectionModel().select(logTab);
        } catch (Exception e) {
            log.error("读取日志文件异常", e);
            new ZkExceptionDialog("读取日志文件异常", e).showUi();
        }
    }

    public void openIssues(ActionEvent actionEvent) {
        try {
            Desktop.getDesktop()
                   .browse(new URI("https://github.com/xin497668869/zookeeper-visualizer/issues/new"));
        } catch (Exception e) {
            log.error("打开issues失败", e);
        }
    }

    public void addNewConnect(ActionEvent actionEvent) {
        ZkConfService.createSaveUi(null, zkConfListView);
    }

    public void toggleLeftPane(ActionEvent actionEvent) {
        leftPaneCollapsed = !leftPaneCollapsed;
        if (leftPaneCollapsed) {
            leftPane.setVisible(false);
            leftPane.setManaged(false);
            leftWrapper.setMinWidth(36);
            leftWrapper.setMaxWidth(36);
            toggleLeftPaneBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #888888; -fx-font-size: 14px; -fx-padding: 0;");
        } else {
            leftPane.setVisible(true);
            leftPane.setManaged(true);
            leftWrapper.setMinWidth(236);
            leftWrapper.setMaxWidth(336);
            Platform.runLater(() -> splitPane.setDividerPositions(0.2));
            toggleLeftPaneBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #aaaaaa; -fx-font-size: 14px; -fx-padding: 0;");
        }
    }

    private void installConfSearchFilter() {
        zkConfListView.setItems(new SearchFilterObservalbeList<>(FXCollections.observableArrayList(ZkConfService.getService()
                                                                                                                .getZkConf()), zkConf -> {
            if (!StringUtil.isEmpty(filterTextField.getText())) {
                return FuzzyMatchUtils.match(zkConf.toString(),
                                             filterTextField.getText());
            } else {
                return true;
            }
        }));
        filterTextField.textProperty()
                       .addListener(observable -> {
                           ((SearchFilterObservalbeList) zkConfListView.getItems()).refilter();
                       });
    }

}
