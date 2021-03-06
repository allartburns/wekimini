/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package wekimini.gui;

import java.text.DecimalFormat;
import javax.swing.DefaultComboBoxModel;

/**
 *
 * @author rebecca
 */
public class NameDropdownRow extends javax.swing.JPanel {

    public NameDropdownRow() {
        initComponents();
    }
    /**
     * Creates new form GUIInputRow
     */
    public NameDropdownRow(String name, String[] comboValues) {
        initComponents();
        labelName.setText(name);
        comboBox.setModel(new DefaultComboBoxModel(comboValues));
    }
    
    public void setSelectedIndex(int value) {
        comboBox.setSelectedIndex(value);
    }
    
    public int getSelectedIndex() {
        return comboBox.getSelectedIndex();
    }
    
    public String getSelectedItem() {
        return (String)comboBox.getSelectedItem();
    }
    
    public String getNameValue() {
        return labelName.getText();
    }
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        labelName = new javax.swing.JLabel();
        comboBox = new javax.swing.JComboBox();

        setBackground(new java.awt.Color(255, 255, 255));
        setMaximumSize(new java.awt.Dimension(32767, 25));
        setLayout(new java.awt.GridLayout());

        labelName.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        labelName.setText("1. HID Inputs");
        add(labelName);

        comboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        add(comboBox);
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JComboBox comboBox;
    private javax.swing.JLabel labelName;
    // End of variables declaration//GEN-END:variables
}
