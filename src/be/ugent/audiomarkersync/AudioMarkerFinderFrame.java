package be.ugent.audiomarkersync;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.UIManager;


public class AudioMarkerFinderFrame extends JFrame {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -2191344236823491369L;
	JTextArea textArea;
	StringBuilder sb = new StringBuilder();
	public AudioMarkerFinderFrame() {
		this.setLayout(new BorderLayout());
	
				
		JButton folderButton = new JButton("Choose folder...");
		folderButton.addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent e) {

				 final JFileChooser
				    chooser = new JFileChooser(); 
				    chooser.setCurrentDirectory(new java.io.File("."));
				    chooser.setDialogTitle("Choose folder");
				    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				    //
				    // disable the "All files" option.
				    //
				    chooser.setAcceptAllFileFilterUsed(false);
				    //    
				    if (chooser.showOpenDialog(AudioMarkerFinderFrame.this) == JFileChooser.APPROVE_OPTION) { 
				      System.out.println("getCurrentDirectory(): " 
				         +  chooser.getCurrentDirectory());
				      System.out.println("getSelectedFile() : " 
				         +  chooser.getSelectedFile());
				      
				      
				      javax.swing.SwingUtilities.invokeLater(new Runnable() {
							public void run() {
								 try {
							    	  
										AudioMarkerFinderFrame.this.processFolder(chooser.getSelectedFile());
									} catch (IOException e1) {
										// TODO Auto-generated catch block
										e1.printStackTrace();
									}
							}
						});
				      
				      }
				    else {
				      System.out.println("No Selection ");
				      }
			}
		});
	
		textArea = new JTextArea();
		textArea.setEditable(false);
		this.add(folderButton,BorderLayout.NORTH);
		
		this.add(new JScrollPane(textArea),BorderLayout.CENTER);
	}
	
	public void processFolder(File folder) throws IOException {
		
		sb = new StringBuilder();
		sb.append("Filename\t;\tMarker start\t;\tScore (%)\n");
		
		final PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:**");
		
		Files.walkFileTree(Paths.get(folder.getAbsolutePath()), new SimpleFileVisitor<Path>() {
			
			@Override
			public FileVisitResult visitFile(Path path,
					BasicFileAttributes attrs) throws IOException {
				if (pathMatcher.matches(path)) {
					//System.out.println(path);
					processFile(path);
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException exc)
					throws IOException {
				return FileVisitResult.CONTINUE;
			}
		});
		
		textArea.setText(textArea.getText() + "\n\n" + sb.toString());
		
	}
	
	public void processFile(Path filePath) {
		System.out.println(filePath.getFileName());
		
		String source  = filePath.toFile().getAbsolutePath();
		
		List<MarkerEvent> markers = new AudioMarkerFinder(source).findMarkers();
		
		for(MarkerEvent e : markers) {
			sb.append(filePath.getFileName())
			.append("\t;\t")
			.append(String.format("%.3f", e.timestamp))
			.append("\t;\t")
			.append(String.format("%.1f", e.score))
			.append("\n");
		}
	}

	public static void main(String...strings) {
		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				try {
					UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
				} catch (Exception e) {
				}
				AudioMarkerFinderFrame frame = new AudioMarkerFinderFrame();
				frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
				frame.pack();
				frame.setTitle("Audio marker finder");
                frame.pack();
                frame.setSize(frame.getWidth() + 550,frame.getHeight() + 300);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
			}
		});
	}
	
}
