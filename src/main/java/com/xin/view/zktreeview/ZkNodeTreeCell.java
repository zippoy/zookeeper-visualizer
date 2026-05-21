package com.xin.view.zktreeview;

import com.xin.ZkClientWrap;
import com.xin.ZkNode;
import com.xin.controller.NodeAddController;
import javafx.beans.value.ChangeListener;
import javafx.event.ActionEvent;
import javafx.event.EventDispatcher;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Optional;

import static javafx.scene.input.MouseEvent.MOUSE_PRESSED;

/**
 * @author 497668869@qq.com
 * @since 1.0
 */
@Slf4j
public class ZkNodeTreeCell extends TreeCell<ZkNode> {
    private final ZkTreeView zkTreeView;
    private final ZkClientWrap zkClientWrap;
    EventHandler<MouseEvent> mouseEventEventHandler = event -> {
        getSelectionModel().clearSelection();
        event.consume();
    };
    private EventHandler<ActionEvent> deleteNodeAction = getDeleteNodeAction();
    private EventHandler<ActionEvent> addNodeAction = getAddNodeAction();
    private EventHandler<ActionEvent> expandNodeAction = getExpandNodeAction();
    private EventHandler<ActionEvent> unExpandNodeAction = getUnExpandNodeAction();
    static final String OPEN_FOLDER_ICON_STYLE_CLASS = "zk-node-open-folder-icon";
    static final String CLOSED_FOLDER_ICON_STYLE_CLASS = "zk-node-closed-folder-icon";
    static final String FILE_ICON_STYLE_CLASS = "zk-node-file-icon";
    private static final Image OPEN_FOLDER_ICON = loadIcon("icons/folder_open.png");
    private static final Image CLOSED_FOLDER_ICON = loadIcon("icons/folder_closed.png");
    private static final Image FILE_ICON = loadIcon("icons/file.png");
    private final ChangeListener<Boolean> expandedIconChangeListener = (observable, oldValue, newValue) -> updateNodeIcon();
    private TreeItem<ZkNode> iconTreeItem;

    public ZkNodeTreeCell(ZkTreeView zkTreeView, ZkClientWrap zkClientWrap) {
        this.zkTreeView = zkTreeView;
        this.zkClientWrap = zkClientWrap;
    }

    public boolean deleteZkNode(TreeItem<ZkNode> zkNodeTreeItem) {
        log.info("准备删除节点 " + zkNodeTreeItem.getValue()
                                           .getPath());
        try {
            return zkClientWrap.deleteRecursive(zkNodeTreeItem.getValue()
                                                              .getPath());
        } catch (Exception e) {
            log.error("删除节点失败 ", e);
        }

        return false;
    }

    @Override
    protected void updateItem(ZkNode item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || getIndex() < 0) {
            uninstallExpandedIconListener();
            setText(null);
            setGraphic(null);
            addEventFilter(MouseEvent.MOUSE_CLICKED, mouseEventEventHandler);
        } else {

            setContentDisplay(ContentDisplay.LEFT);
            setTextFill(Color.BLACK);
            setText(item.getName());
            installExpandedIconListener();
            updateNodeIcon();

            installContextMenu();

            removeEventFilter(MouseEvent.MOUSE_CLICKED, mouseEventEventHandler);
        }


        setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                triggertExpand();
            }
        });
//        treeItemDoubleClick();
    }

    private void updateNodeIcon() {
        ZkNode item = getItem();
        TreeItem<ZkNode> treeItem = getTreeItem();
        if (item != null && treeItem != null) {
            setGraphic(createNodeIcon(item.isHasChildren(), treeItem.isExpanded()));
        }
    }

    private void installExpandedIconListener() {
        TreeItem<ZkNode> currentTreeItem = getTreeItem();
        if (iconTreeItem == currentTreeItem) {
            return;
        }
        uninstallExpandedIconListener();
        iconTreeItem = currentTreeItem;
        if (iconTreeItem != null) {
            iconTreeItem.expandedProperty().addListener(expandedIconChangeListener);
        }
    }

    private void uninstallExpandedIconListener() {
        if (iconTreeItem != null) {
            iconTreeItem.expandedProperty().removeListener(expandedIconChangeListener);
            iconTreeItem = null;
        }
    }

    static Node createNodeIcon(boolean hasChildren, boolean expanded) {
        ImageView icon = new ImageView(getNodeIcon(hasChildren, expanded));
        icon.getStyleClass().add(getNodeIconStyleClass(hasChildren, expanded));
        return icon;
    }

    private static Image getNodeIcon(boolean hasChildren, boolean expanded) {
        if (!hasChildren) {
            return FILE_ICON;
        }
        return expanded ? OPEN_FOLDER_ICON : CLOSED_FOLDER_ICON;
    }

    private static String getNodeIconStyleClass(boolean hasChildren, boolean expanded) {
        if (!hasChildren) {
            return FILE_ICON_STYLE_CLASS;
        }
        return expanded ? OPEN_FOLDER_ICON_STYLE_CLASS : CLOSED_FOLDER_ICON_STYLE_CLASS;
    }

    private static Image loadIcon(String path) {
        return new Image(ZkNodeTreeCell.class.getClassLoader().getResourceAsStream(path));
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new TreeCellSkin<>(this);
    }

    private void triggertExpand() {
        TreeItem<ZkNode> selectedItem = getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            selectedItem.setExpanded(!selectedItem.isExpanded());
        }
    }

    private void treeItemDoubleClick() {
        EventDispatcher eventDispatcher = getEventDispatcher();
        setEventDispatcher((event, tail) -> {
            if (event instanceof MouseEvent) {
                if (((MouseEvent) event).getButton() == MouseButton.PRIMARY
                        && event.getEventType()
                                .equals(MOUSE_PRESSED)
                        && ((MouseEvent) event).getClickCount() == 2) {

                    return event;
                }
            }
            return eventDispatcher.dispatchEvent(event, tail);
        });
    }

    private SelectionModel<TreeItem<ZkNode>> getSelectionModel() {
        return zkTreeView.getSelectionModel();
    }

    private void installContextMenu() {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem addNode = new MenuItem("新增节点");
        addNode.setOnAction(addNodeAction);
        contextMenu.getItems()
                   .add(addNode);

        MenuItem deleteNode = new MenuItem("删除节点");
        deleteNode.setOnAction(deleteNodeAction);
        contextMenu.getItems()
                   .add(deleteNode);

        MenuItem expandNode = new MenuItem("展开所有节点");
        expandNode.setOnAction(expandNodeAction);
        contextMenu.getItems()
                   .add(expandNode);

        MenuItem unExpandNode = new MenuItem("收缩所有节点");
        unExpandNode.setOnAction(unExpandNodeAction);
        contextMenu.getItems()
                   .add(unExpandNode);
        setContextMenu(contextMenu);

    }

    private EventHandler<ActionEvent> getUnExpandNodeAction() {
        return event -> {
            ZkNode value = getSelectionModel().getSelectedItem()
                                              .getValue();
            unExpandAllChildren(value);
        };
    }

    private EventHandler<ActionEvent> getExpandNodeAction() {
        return event -> {
            ZkNode value = getSelectionModel().getSelectedItem()
                                              .getValue();
            expandAllChildren(value);
        };

    }

    private void expandAllChildren(ZkNode zkNode) {
        zkNode.getTreeItem()
              .setExpanded(true);
        if (zkNode.getChildren() == null) {
            return;
        }
        for (ZkNode child : zkNode.getChildren()) {
            expandAllChildren(child);
        }
    }

    private void unExpandAllChildren(ZkNode zkNode) {
        zkNode.getTreeItem()
              .setExpanded(false);
        if (zkNode.getChildren() == null) {
            return;
        }
        for (ZkNode child : zkNode.getChildren()) {
            unExpandAllChildren(child);
        }
    }

    private EventHandler<ActionEvent> getDeleteNodeAction() {
        return event -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("删除节点提示");
            alert.setHeaderText(null);
            alert.setContentText("准备删除节点 path: " + getSelectionModel().getSelectedItem()
                                                                      .getValue()
                                                                      .getPath());
            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                if (!deleteZkNode(getSelectionModel().getSelectedItem())) {
                    Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                    errorAlert.setTitle("节点删除失败 path: " + getSelectionModel().getSelectedItem()
                                                                             .getValue()
                                                                             .getPath());
                    errorAlert.setHeaderText(null);
                    errorAlert.setContentText("准备删除节点 ");
                    errorAlert.showAndWait();
                }
            }
        };
    }

    private EventHandler<ActionEvent> getAddNodeAction() {
        return new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                try {
                    Parent parent = FXMLLoader.load(getClass().getResource("/fxml/nodeAdd.fxml"));
                    Label parentPathLabel = (Label) parent.lookup("#parentPathLabel");
                    TreeItem<ZkNode> selectedItem = getSelectionModel()
                            .getSelectedItem();
                    if ("/".equals(selectedItem.getValue()
                                               .getPath())) {
                        parentPathLabel.setText(selectedItem.getValue()
                                                            .getPath());
                    } else {
                        parentPathLabel.setText(selectedItem.getValue()
                                                            .getPath() + "/");
                    }
                    Stage stage = new Stage();
                    stage.initModality(Modality.APPLICATION_MODAL);
                    stage.setTitle("zk节点新增");
                    Scene scene = new Scene(parent);
                    stage.setScene(scene);
                    stage.showAndWait();

                    NodeAddController.NodeAddConf nodeAddConf = (NodeAddController.NodeAddConf) scene.getUserData();
                    if (nodeAddConf != null) {
                        log.info("准备创建zk节点 " + nodeAddConf);
                        zkClientWrap.create(nodeAddConf.getPath(), nodeAddConf.getValue(), nodeAddConf.getZkNodeType());
                    }

                } catch (IOException e) {
                    log.error("生成nodeAdd控件失败", e);
                }
            }
        };
    }
}
