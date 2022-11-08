package domain;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.net.InternetDomainName;

import GUI.GUIMain;
import GUI.JScrollPanelWithHeader;
import burp.BurpExtender;
import burp.Commons;
import burp.IBurpExtenderCallbacks;
import burp.IHttpRequestResponse;
import burp.IHttpService;
import burp.IScanIssue;
import config.ConfigPanel;
import dao.DomainDao;
import dao.TargetDao;
import domain.target.TargetControlPanel;
import domain.target.TargetEntry;
import domain.target.TargetTable;
import domain.target.TargetTableModel;
import thread.ThreadSearhDomain;
import toElastic.VMP;

/*
 *注意，所有直接对DomainObject中数据的修改，都不会触发该tableChanged监听器。
 *1、除非操作的逻辑中包含了firexxxx来主动通知监听器。比如DomainPanel.domainTableModel.fireTableChanged(null);
 *2、或者主动调用显示和保存的函数直接完成，不经过监听器。
	//GUI.getDomainPanel().showToDomainUI();
	//DomainPanel.autoSave();
 */
public class DomainPanel extends JPanel {

	private JButton btnSearch;
	private JButton btnCrawl;
	private JLabel lblSummary;

	JScrollPanelWithHeader ScrollPaneSpecialPortTargets;
	JScrollPanelWithHeader ScrollPaneRelatedDomains;
	JScrollPanelWithHeader ScrollPaneSubdomains;
	JScrollPanelWithHeader ScrollPaneSimilarDomains;
	JScrollPanelWithHeader ScrollPaneEmails;
	JScrollPanelWithHeader ScrollPanePackageNames;

	JScrollPanelWithHeader PanelIPOfSubnet;
	JScrollPanelWithHeader PanelIPOfCert;
	JScrollPanelWithHeader PanelBlackIPList;
	JScrollPanelWithHeader PanelSimilarEmails;

	private TargetTable targetTable;
	private JPanel HeaderPanel;
	private TargetControlPanel ControlPanel;

	//流量分析进程需要用到这个变量，标记为volatile以获取正确的值。
	/**
	 * DomainPanel对应DomainManager
	 * TargetTable对应TargetTableModel
	 *
	 * targetTable可以从DomainPanel中获取，是GUI的线路关系
	 * targetTableModel则对应地从DomainManager中的对象获取，是数据模型的线路关系
	 */
	private volatile DomainManager domainResult;//getter setter
	private boolean listenerIsOn = true;
	private DomainDao domainDao;
	private TargetDao targetDao;
	private PrintWriter stdout;
	private PrintWriter stderr;
	private GUIMain guiMain;

	private static final Logger log = LogManager.getLogger(DomainPanel.class);


	public boolean isListenerIsOn() {
		return listenerIsOn;
	}

	public void setListenerIsOn(boolean listenerIsOn) {
		this.listenerIsOn = listenerIsOn;
	}

	public TargetTable getTargetTable() {
		return targetTable;
	}

	public void setTargetTable(TargetTable targetTable) {
		this.targetTable = targetTable;
	}

	public TargetTableModel fetchTargetModel() {
		return targetTable.getTargetModel();
	}

	public JLabel getLblSummary() {
		return lblSummary;
	}

	public JPanel getHeaderPanel() {
		return HeaderPanel;
	}

	public TargetControlPanel getControlPanel() {
		return ControlPanel;
	}

	public void setControlPanel(TargetControlPanel controlPanel) {
		ControlPanel = controlPanel;
	}

	public DomainManager getDomainResult() {
		return domainResult;
	}

	public void setDomainResult(DomainManager domainResult) {
		this.domainResult = domainResult;
	}

	public void setLblSummary(JLabel lblSummary) {
		this.lblSummary = lblSummary;
	}

	public void setHeaderPanel(JPanel headerPanel) {
		HeaderPanel = headerPanel;
	}

	
	public GUIMain getGuiMain() {
		return guiMain;
	}

	public void setGuiMain(GUIMain guiMain) {
		this.guiMain = guiMain;
	}

	
	public DomainDao getDomainDao() {
		return domainDao;
	}

	public void setDomainDao(DomainDao domainDao) {
		this.domainDao = domainDao;
	}

	public TargetDao getTargetDao() {
		return targetDao;
	}

	public void setTargetDao(TargetDao targetDao) {
		this.targetDao = targetDao;
	}

	public void createOrOpenDB() {
		Object[] options = { "Create","Open"};
		int user_input = JOptionPane.showOptionDialog(null, "You should Create or Open a DB file", "Chose Your Action",
				JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);
		if (user_input == 0) {
			guiMain.getProjectMenu().createNewDb(guiMain);
		}
		if (user_input == 1) {
			guiMain.getProjectMenu().openDb();
		}
	}


	public DomainPanel(GUIMain guiMain) {//构造函数
		this.guiMain = guiMain;
		this.setBorder(new EmptyBorder(5, 5, 5, 5));
		this.setLayout(new BorderLayout(0, 0));

		try {
			stdout = new PrintWriter(BurpExtender.getCallbacks().getStdout(), true);
			stderr = new PrintWriter(BurpExtender.getCallbacks().getStderr(), true);
		} catch (Exception e) {
			stdout = new PrintWriter(System.out, true);
			stderr = new PrintWriter(System.out, true);
		}


		///////////////////////HeaderPanel//////////////


		HeaderPanel = new JPanel();
		FlowLayout fl_HeaderPanel = (FlowLayout) HeaderPanel.getLayout();
		fl_HeaderPanel.setAlignment(FlowLayout.LEFT);
		this.add(HeaderPanel, BorderLayout.NORTH);

		JButton btnSaveDomainOnly = new JButton("SaveDomainOnly");
		btnSaveDomainOnly.setToolTipText("Only save data in Domain Panel");
		btnSaveDomainOnly.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				saveDomainOnly();
			}
		});
		HeaderPanel.add(btnSaveDomainOnly);

		
		JButton test = new JButton("test");
		test.setToolTipText("Only save data in Domain Panel");
		test.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				guiMain.stopLiveCapture();
			}});
		HeaderPanel.add(test);

		

		/*
		btnBrute = new JButton("Brute");
		btnBrute.setToolTipText("Do Brute for all root domains");
		btnBrute.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				SwingWorker<Map, Map> worker = new SwingWorker<Map, Map>() {
					//using SwingWorker to prevent blocking burp main UI.

					@Override
					protected Map doInBackground() throws Exception {

						Set<String> rootDomains = domainResult.fetchRootDomainSet();
						Set<String> keywords= domainResult.fetchKeywordSet();

						//stderr.print(keywords.size());
						//System.out.println(rootDomains.toString());
						//System.out.println("xxx"+keywords.toString());
						btnBrute.setEnabled(false);
						//						threadBruteDomain = new ThreadBruteDomain(rootDomains);
						//						threadBruteDomain.Do();
						for (String rootDomain: rootDomains){
							threadBruteDomain2 = new ThreadBruteDomainWithDNSServer2(rootDomain);
							threadBruteDomain2.Do();
						}


						return null;
					}
					@Override
					protected void done() {
						try {
							get();
							showToDomainUI();
							autoSave();
							btnBrute.setEnabled(true);
							stdout.println("~~~~~~~~~~~~~brute Done~~~~~~~~~~~~~");
						} catch (Exception e) {
							btnBrute.setEnabled(true);
							e.printStackTrace(stderr);
						}
					}
				};
				worker.execute();
			}
		});

		Component verticalStrut = Box.createVerticalStrut(20);
		HeaderPanel.add(verticalStrut);
		//HeaderPanel.add(btnBrute);
		 */


		btnSearch = new JButton("Search");
		btnSearch.setToolTipText("Do a single search from site map");
		btnSearch.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				SwingWorker<Map, Map> worker = new SwingWorker<Map, Map>() {
					//using SwingWorker to prevent blocking burp main UI.
					@Override
					protected Map doInBackground() throws Exception {
						Set<String> rootDomains = fetchTargetModel().fetchTargetDomainSet();
						Set<String> keywords = fetchTargetModel().fetchKeywordSet();

						//stderr.print(keywords.size());
						//System.out.println(rootDomains.toString());
						//System.out.println("xxx"+keywords.toString());
						btnSearch.setEnabled(false);
						domainResult.getEmailSet().addAll(collectEmails());
						return search(rootDomains, keywords);
					}

					@Override
					protected void done() {
						try {
							get();
							showDataToDomainGUI();
							saveDomainDataToDB();
							btnSearch.setEnabled(true);
						} catch (Exception e) {
							btnSearch.setEnabled(true);
							e.printStackTrace(stderr);
						}
					}
				};
				worker.execute();
			}
		});

		HeaderPanel.add(btnSearch);

		btnCrawl = new JButton("Crawl");
		btnCrawl.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {

				SwingWorker<Map, Map> worker = new SwingWorker<Map, Map>() {
					//可以在一个类中实现另一个类，直接实现原始类，没有变量处理的困扰；
					//之前的想法是先单独实现一个worker类，在它里面处理各种，就多了一层实现，然后在这里调用，变量调用会是一个大问题。
					//https://stackoverflow.com/questions/19708646/how-to-update-swing-ui-while-actionlistener-is-in-progress
					@Override
					protected Map doInBackground() throws Exception {
						Set<String> rootDomains = fetchTargetModel().fetchTargetDomainSet();
						Set<String> keywords = fetchTargetModel().fetchKeywordSet();

						btnCrawl.setEnabled(false);
						return crawl(rootDomains, keywords);

					}

					@Override
					protected void done() {
						try {
							get();
							showDataToDomainGUI();
							saveDomainDataToDB();
							btnCrawl.setEnabled(true);
						} catch (Exception e) {
							e.printStackTrace(stderr);
						}
					}
				};
				worker.execute();
			}
		});
		btnCrawl.setToolTipText("Crawl all subdomains recursively,This may take a long time and large Memory Usage!!!");
		HeaderPanel.add(btnCrawl);


		JButton btnZoneTransferCheck = new JButton("AXFR");
		btnZoneTransferCheck.setToolTipText("Zone Transfer Check");
		btnZoneTransferCheck.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {

				SwingWorker<Map, Map> worker = new SwingWorker<Map, Map>() {
					@Override
					protected Map doInBackground() throws Exception {
						stdout.println("~~~~~~~~~~~~~Zone Transfer Checking~~~~~~~~~~~~~");
						btnZoneTransferCheck.setEnabled(false);
						fetchTargetModel().ZoneTransferCheckAll();
						return null;
					}

					@Override
					protected void done() {
						btnZoneTransferCheck.setEnabled(true);
						stdout.println("~~~~~~~~~~~~~Zone Transfer Check Done~~~~~~~~~~~~~");
					}
				};
				worker.execute();
			}
		});
		HeaderPanel.add(btnZoneTransferCheck);

		JButton btnBuckupDB = new JButton("Backup DB");
		btnBuckupDB.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				backupDB();
			}
		});
		HeaderPanel.add(btnBuckupDB);




		JButton btnUpload = new JButton("Upload");
		btnUpload.setToolTipText("upload data to Server");
		btnUpload.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				SwingWorker<Boolean, Boolean> worker = new SwingWorker<Boolean, Boolean>() {
					@Override
					protected Boolean doInBackground() throws Exception {
						btnUpload.setEnabled(false);
						String url = guiMain.getConfigPanel().getLineConfig().getUploadUrl();
						String host = new URL(url).getHost();
						String token = ConfigPanel.textFieldUploadApiToken.getText().trim();
						HashMap<String, String> headers = new HashMap<String, String>();
						headers.put("Content-Type", "application/json;charset=UTF-8");
						if (token != null && !token.equals("")) {//vmp
							headers.put("Authorization", "Token " + token);
						}
						if (host.startsWith("vmp.test.shopee.") ||
								host.contains("burpcollaborator.net") ||
								host.contains("vmp.sz.shopee")) {
							return new VMP(guiMain).uploadAllVMPEntries(url, headers);
						} else {//只上传域名信息
							return VMP.upload(url, headers, domainResult.ToJson());
						}
					}

					@Override
					protected void done() {
						//Do Nothing
						btnUpload.setEnabled(true);
					}
				};
				worker.execute();
			}
		});
		HeaderPanel.add(btnUpload);


		////////////////////////////////////Body Panel area///////////////////////////////////////////////////////



		//布局参考		https://docs.oracle.com/javase/tutorial/uiswing/layout/visual.html
		//GridBagLayout ---类似Excel的合并单元格，也不可拖动边框大小
		//GridLayout ---类似Excel，但是不可拖动格子边框


		////////////////Body的左边部分，对应sitemap的位置，存放目标规则//////////////
		JSplitPane TargetPane = new JSplitPane();//中间的大模块，一分为二
		TargetPane.setOrientation(JSplitPane.VERTICAL_SPLIT);
		TargetPane.setResizeWeight(1);
		this.add(TargetPane, BorderLayout.WEST);

		JScrollPane PanelWest1 = new JScrollPane();
		TargetPane.setLeftComponent(PanelWest1);
		targetTable = new TargetTable(guiMain);
		PanelWest1.setViewportView(targetTable);

		ControlPanel = new TargetControlPanel(this);
		TargetPane.setRightComponent(ControlPanel);


		////////////////Body的右边部分，对应sitemap的位置，存放目标规则//////////////
		JPanel BodyPane = new JPanel();//中间的大模块，一分为二
		this.add(BodyPane, BorderLayout.CENTER);
		BodyPane.setLayout(new GridLayout(2, 5, 0, 0));


		ScrollPaneRelatedDomains = new JScrollPanelWithHeader(this,TextAreaType.RelatedDomain,"Related Domains","Related Domains"); //E2
		ScrollPaneSubdomains = new JScrollPanelWithHeader(this,TextAreaType.SubDomain,"Sub Domains","Sub Domains");
		ScrollPaneSimilarDomains = new JScrollPanelWithHeader(this,TextAreaType.SimilarDomain,"Similar Domains","Similar Domains");
		ScrollPaneEmails = new JScrollPanelWithHeader(this,TextAreaType.Email,"Emails","Emails");
		PanelSimilarEmails = new JScrollPanelWithHeader(this,TextAreaType.SimilarEmail,"Similar Emails","Similar Emails");

		ScrollPaneSpecialPortTargets = new JScrollPanelWithHeader(this,TextAreaType.SpecialPortTarget,"Custom Assets","you can put your custom assets here");
		PanelIPOfSubnet = new JScrollPanelWithHeader(this,TextAreaType.IPSetOfSubnet,"IP Of Subnet","IP Of Subnet");
		PanelIPOfCert = new JScrollPanelWithHeader(this,TextAreaType.IPSetOfCert,"IP Of Cert","IP Of Cert");
		ScrollPanePackageNames = new JScrollPanelWithHeader(this,TextAreaType.PackageName,"Package Names","Package Names");
		PanelBlackIPList = new JScrollPanelWithHeader(this,TextAreaType.BlackIP,"Black IP List","Black IP List");

		BodyPane.add(ScrollPaneRelatedDomains);
		BodyPane.add(ScrollPaneSubdomains);
		BodyPane.add(ScrollPaneSimilarDomains);
		BodyPane.add(ScrollPaneEmails);
		BodyPane.add(PanelSimilarEmails);
		BodyPane.add(ScrollPaneSpecialPortTargets);
		BodyPane.add(PanelIPOfSubnet);
		BodyPane.add(PanelIPOfCert);
		BodyPane.add(ScrollPanePackageNames);
		BodyPane.add(PanelBlackIPList);


		///////////////////////////FooterPanel//////////////////


		JPanel footerPanel = new JPanel();
		FlowLayout fl_FooterPanel = (FlowLayout) footerPanel.getLayout();
		fl_FooterPanel.setAlignment(FlowLayout.LEFT);
		this.add(footerPanel, BorderLayout.SOUTH);

		JLabel footerLabel = new JLabel(BurpExtender.getGithub());
		footerLabel.setFont(new Font("宋体", Font.BOLD, 12));
		footerLabel.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				try {
					URI uri = new URI(BurpExtender.getGithub());
					Desktop desktop = Desktop.getDesktop();
					if (Desktop.isDesktopSupported() && desktop.isSupported(Desktop.Action.BROWSE)) {
						desktop.browse(uri);
					}
				} catch (Exception e2) {
					e2.printStackTrace(stderr);
				}
			}

			@Override
			public void mouseEntered(MouseEvent e) {
				footerLabel.setForeground(Color.BLUE);
			}

			@Override
			public void mouseExited(MouseEvent e) {
				footerLabel.setForeground(Color.BLACK);
			}
		});
		footerPanel.add(footerLabel);

		lblSummary = new JLabel("      ^_^");
		footerPanel.add(lblSummary);
		lblSummary.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {//左键双击
					try {
						Commons.OpenFolder(guiMain.getCurrentDBFile().getParent());
					} catch (Exception e2) {
						e2.printStackTrace(stderr);
					}
				}
				if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 1) {//左键双击
					lblSummary.setText(domainResult.getSummary());
				}
			}

			@Override
			public void mouseEntered(MouseEvent e) {
				lblSummary.setForeground(Color.RED);
				lblSummary.setToolTipText(guiMain.getCurrentDBFile().toString());
			}

			@Override
			public void mouseExited(MouseEvent e) {
				lblSummary.setForeground(Color.BLACK);
			}
		});

	}


	/**
	 * 显示DomainPanel中的数据。
	 * 无论是用户的手动输入编辑文本框内容，还是调用setText方法，都将触发DocumentListener!(见JTextAreaListenerTest.java)
	 * 所以show()函数的设计都应该关闭监听器的开关。
	 * 
	 * DomainManager ---> UI
	 */
	public void showDataToDomainGUI() {
		listenerIsOn = false;

		ScrollPaneSpecialPortTargets.getTextArea().setText(domainResult.fetchSpecialPortTargets());
		ScrollPaneSubdomains.getTextArea().setText(domainResult.fetchSubDomains());
		ScrollPaneSimilarDomains.getTextArea().setText(domainResult.fetchSimilarDomains());
		ScrollPaneRelatedDomains.getTextArea().setText(domainResult.fetchRelatedDomains());
		ScrollPaneEmails.getTextArea().setText(domainResult.fetchEmails());
		ScrollPanePackageNames.getTextArea().setText(domainResult.fetchPackageNames());

		PanelIPOfSubnet.getTextArea().setText(domainResult.fetchIPSetOfSubnet());
		PanelIPOfCert.getTextArea().setText(domainResult.fetchIPSetOfCert());
		PanelBlackIPList.getTextArea().setText(domainResult.fetchIPBlackList());
		PanelSimilarEmails.getTextArea().setText(domainResult.fetchSimilarEmails());


		lblSummary.setText(domainResult.getSummary());
		ControlPanel.getRdbtnAddRelatedToRoot().setSelected(domainResult.autoAddRelatedToRoot);

		System.out.println("Load Domain Panel Data Done, " + domainResult.getSummary());
		stdout.println("Load Domain Panel Data Done, " + domainResult.getSummary());

		listenerIsOn = true;
	}

	/**
	 * 数据加载过程分为两步：
	 * 1、从DB到DomainManager；
	 * 2、从DomainManager到UI。
	 * @param dbFilePath
	 * 
	 * DB---> DomainManager ---> UI
	 */
	public void LoadDomainData(String dbFilePath) {
		domainDao = new DomainDao(dbFilePath);
		domainResult = domainDao.getDomainManager();
		domainResult.setGuiMain(guiMain);
		setDomainResult(domainResult);
		showDataToDomainGUI();
	}

	/**
	 * DomainManager ---> DB
	 */
	public void saveDomainDataToDB() {
		try {
			File file = guiMain.getCurrentDBFile();
			if (file == null || !file.exists()) {
				file = guiMain.dbfc.dialog(false,".db");
				guiMain.setCurrentDBFile(file);
			}
			DomainDao dao = new DomainDao(file.toString());
			dao.saveDomainManager(domainResult);
			log.info("domain data saved");
		} catch (Exception e) {
			e.printStackTrace();
			e.printStackTrace(BurpExtender.getStderr());
		}
	}

	/**
	 * 加载数据的的方式就是重新设置TableModel
	 * @param dbFilePath
	 */
	public void LoadTargetsData(String dbFilePath) {
		targetDao = new TargetDao(dbFilePath);
		List<TargetEntry> targets = targetDao.selectAll();
		TargetTableModel targetModel = new TargetTableModel(guiMain,targets);
		targetTable.setModel(targetModel);
	}


	//////////////////////////////methods//////////////////////////////////////
	/*
    执行完成后，就已将数据保存到了domainResult
	 */
	public Map<String, Set<String>> search(Set<String> rootdomains, Set<String> keywords) {
		IBurpExtenderCallbacks callbacks = BurpExtender.getCallbacks();
		IHttpRequestResponse[] messages = callbacks.getSiteMap(null);

		List<IHttpRequestResponse> AllMessages = new ArrayList<IHttpRequestResponse>();
		AllMessages.addAll(Arrays.asList(messages));
		AllMessages.addAll(collectPackageNameMessages());//包含错误回显的请求响应消息

		ThreadSearhDomain searchinstance = new ThreadSearhDomain(guiMain,AllMessages);
		searchinstance.start();
		try {
			searchinstance.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		//DomainPanel.saveDomainDataToDB();//SwingWorker中的Done()每次搜索完成都应该进行一次保存
		return null;
	}


	/**
	 * 从issue中提取Email
	 * @return
	 */
	public Set<String> collectEmails() {
		Set<String> Emails = new HashSet<>();
		IScanIssue[] issues = BurpExtender.getCallbacks().getScanIssues(null);

		final String REGEX_EMAIL = "[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+";
		Pattern pDomainNameOnly = Pattern.compile(REGEX_EMAIL);

		for (IScanIssue issue : issues) {
			if (issue.getIssueName().equalsIgnoreCase("Email addresses disclosed")) {
				String detail = issue.getIssueDetail();
				Matcher matcher = pDomainNameOnly.matcher(detail);
				while (matcher.find()) {//多次查找
					String email = matcher.group();
					if (fetchTargetModel().emailType(email) == DomainManager.CERTAIN_EMAIL) {
						Emails.add(matcher.group());
					}
					System.out.println(matcher.group());
				}
			}
		}
		return Emails;
	}


	public static Set<IHttpRequestResponse> collectPackageNameMessages() {
		Set<IHttpRequestResponse> PackageNameMessages = new HashSet<>();
		IScanIssue[] issues = BurpExtender.getCallbacks().getScanIssues(null);
		for (IScanIssue issue : issues) {
			if (issue.getIssueName().toLowerCase().contains("error")) {
				//Detailed Error Messages Revealed  ---generated by other burp extender
				PackageNameMessages.addAll(Arrays.asList(issue.getHttpMessages()));
			}
		}
		return PackageNameMessages;
	}


	public Map<String, Set<String>> crawl(Set<String> rootdomains, Set<String> keywords) {
		int i = 0;
		while (i <= 2) {
			for (String rootdomain : rootdomains) {
				if (!rootdomain.contains(".") || rootdomain.endsWith(".") || rootdomain.equals("")) {
					//如果域名为空，或者（不包含.号，或者点号在末尾的）
				} else {
					IBurpExtenderCallbacks callbacks = BurpExtender.getCallbacks();
					IHttpRequestResponse[] items = callbacks.getSiteMap(null); //null to return entire sitemap
					//int len = items.length;
					//stdout.println("item number: "+len);
					Set<URL> NeedToCrawl = new HashSet<URL>();
					for (IHttpRequestResponse x : items) {// 经过验证每次都需要从头开始遍历，按一定offset获取的数据每次都可能不同

						IHttpService httpservice = x.getHttpService();
						String shortUrlString = httpservice.toString();
						String Host = httpservice.getHost();

						try {
							URL shortUrl = new URL(shortUrlString);

							if (Host.endsWith("." + rootdomain) && Commons.isResponseNull(x)) {
								// to reduce memory usage, use isResponseNull() method to adjust whether the item crawled.
								NeedToCrawl.add(shortUrl);
								// to reduce memory usage, use shortUrl. base on my test, spider will crawl entire site when send short URL to it.
								// this may miss some single page, but the single page often useless for domain collection
								// see spideralltest() function.
							}
						} catch (MalformedURLException e) {
							e.printStackTrace(stderr);
						}
					}


					for (URL shortUrl : NeedToCrawl) {
						if (!callbacks.isInScope(shortUrl)) { //reduce add scope action, to reduce the burp UI action.
							callbacks.includeInScope(shortUrl);//if not, will always show confirm message box.
						}
						callbacks.sendToSpider(shortUrl);
					}
				}
			}


			try {
				Thread.sleep(5 * 60 * 1000);//单位毫秒，60000毫秒=一分钟
				stdout.println("sleep 5 minutes to wait spider");
				//to wait spider
			} catch (InterruptedException e) {
				e.printStackTrace(stdout);
			}
			i++;
		}

		return search(rootdomains, keywords);
	}


	/*
    单独保存域名信息到另外的文件
	 */
	public File saveDomainOnly() {
		try {
			File file = guiMain.dbfc.dialog(false,".db");
			if (file != null) {
				DomainDao dao = new DomainDao(file.toString());
				TargetDao dao1 = new TargetDao(file.toString());
				if (dao.saveDomainManager(domainResult) && dao1.addOrUpdateTargets(fetchTargetModel().getTargetEntries())) {
					stdout.println("Save Domain Only Success! " + Commons.getNowTimeString());
					return file;
				}
			}
		} catch (Exception e) {
			e.printStackTrace(stderr);
		}
		stdout.println("Save Domain Only failed! " + Commons.getNowTimeString());
		return null;
	}


	/**
	 * 从UI文本框到DomainManager的过程。
	 * 由listener负责。
	 */
	@Deprecated
	public void saveTextAreas() {		
		domainResult.getSummary();
	}

	public static Set<String> getSetFromTextArea(JTextArea textarea) {
		//user input maybe use "\n" in windows, so the System.lineSeparator() not always works fine!
		Set<String> domainList = new HashSet<>(Arrays.asList(textarea.getText().replaceAll(" ", "").replaceAll("\r\n", "\n").split("\n")));
		domainList.remove("");
		return domainList;
	}

	public void backupDB() {
		File file = guiMain.getCurrentDBFile();
		if (file == null) return;
		File bakfile = new File(file.getAbsoluteFile().toString() + ".bak" + Commons.getNowTimeString());
		try {
			FileUtils.copyFile(file, bakfile);
			BurpExtender.getStdout().println("DB File Backed Up:" + bakfile.getAbsolutePath());
		} catch (IOException e1) {
			e1.printStackTrace(BurpExtender.getStderr());
		}
	}

	public static void main(String[] args) {
		String tmp = InternetDomainName.from("baidu.xxx.com.br").topPrivateDomain().toString();
		String tmp1 = InternetDomainName.from("baidu.xxx.com.br").publicSuffix().toString();
		System.out.println(tmp);
		System.out.println(tmp1);
	}
}
