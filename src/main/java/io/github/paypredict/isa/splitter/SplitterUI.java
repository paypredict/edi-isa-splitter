package io.github.paypredict.isa.splitter;

import kotlin.Unit;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

public class SplitterUI {

    private JButton actionButton;
    private JPanel mainPanel;
    private JProgressBar progressBar;
    private JButton closeButton;
    private JTextField srcTextField;
    private JTextField dstTextField;
    private JButton srcBrowseButton;
    private JButton dstBrowseButton;

    private SplitterUI() {
        actionButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                doMainAction();
            }

        });
        closeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                doCloseApp();
            }
        });
        srcBrowseButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                browseDirectory(srcTextField);
            }
        });
        dstBrowseButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                browseDirectory(dstTextField);
            }
        });
    }

    private static void browseDirectory(JTextField textField) {
        final JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setSelectedFile(new File(textField.getText()).getAbsoluteFile());
        chooser.showSaveDialog(null);
        textField.setText(chooser.getSelectedFile().getAbsolutePath());
    }

    private final AtomicBoolean splitting = new AtomicBoolean(false);
    private final AtomicBoolean closing = new AtomicBoolean(false);

    private void doCloseApp() {
        if (splitting.get()) {
            closing.set(false);
        } else {
            System.exit(0);
        }
    }

    private void doMainAction() {
        start();
    }

    private void start() {
        actionButton.setEnabled(false);
        final Splitter splitter = new Splitter(
                new File(srcTextField.getText()),
                new File(dstTextField.getText())
        );
        splitter.setOnStart(() -> {
            splitting.set(true);
            SwingUtilities.invokeLater(() -> progressBar.setValue(0));
            return Unit.INSTANCE;
        });
        splitter.setOnFinish(() -> {
            splitting.set(false);
            if (closing.get()) System.exit(0);
            SwingUtilities.invokeLater(() -> {
                actionButton.setEnabled(true);
                JOptionPane.showMessageDialog(mainPanel, "Splitting finished");
            });
            return Unit.INSTANCE;
        });
        splitter.setOnError(error -> {
            splitting.set(false);
            SwingUtilities.invokeLater(() -> {
                actionButton.setEnabled(true);
                JOptionPane.showMessageDialog(mainPanel, error.getMessage());
            });
            return Unit.INSTANCE;
        });
        splitter.setOnProgress(progress -> {
            SwingUtilities.invokeLater(() -> {
                progressBar.setMaximum(progress.getMax());
                progressBar.setValue(progress.getValue());
            });
            return closing.get() ? OnProgressRes.CLOSE : OnProgressRes.CONTINUE;
        });
        splitter.start();
    }

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        JFrame.setDefaultLookAndFeelDecorated(true);

        final SplitterUI ui = new SplitterUI();
        final JFrame jFrame = new JFrame("EDI Claim Sorter (837/835 docs)");
        jFrame.setContentPane(ui.mainPanel);
        jFrame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        jFrame.pack();
        final Dimension size = jFrame.getSize();
        size.width = Math.max(540, size.width);
        jFrame.setSize(size);
        jFrame.setLocationRelativeTo(null);
        jFrame.setVisible(true);
    }
}
