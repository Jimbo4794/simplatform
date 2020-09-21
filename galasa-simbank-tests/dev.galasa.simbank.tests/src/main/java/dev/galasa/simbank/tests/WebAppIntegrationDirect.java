package dev.galasa.simbank.tests;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Random;

import org.apache.commons.logging.Log;
import org.assertj.core.api.Fail;

import dev.galasa.Test;
import dev.galasa.artifact.BundleResources;
import dev.galasa.artifact.IBundleResources;
import dev.galasa.core.manager.Logger;
import dev.galasa.core.manager.StoredArtifactRoot;
import dev.galasa.http.HttpClient;
import dev.galasa.http.IHttpClient;
import dev.galasa.selenium.IFirefoxOptions;
import dev.galasa.selenium.ISeleniumManager;
import dev.galasa.selenium.IWebPage;
import dev.galasa.selenium.SeleniumManager;
import dev.galasa.simbank.manager.ISimBank;
import dev.galasa.simbank.manager.ISimBankTerminal;
import dev.galasa.simbank.manager.ISimBankWebApp;
import dev.galasa.simbank.manager.SimBank;
import dev.galasa.simbank.manager.SimBankTerminal;
import dev.galasa.simbank.manager.SimBankWebApp;
import dev.galasa.zos.IZosImage;
import dev.galasa.zos.ZosImage;
import dev.galasa.zos3270.spi.NetworkException;
import dev.galasa.zosbatch.IZosBatch;
import dev.galasa.zosbatch.IZosBatchJob;
import dev.galasa.zosbatch.IZosBatchJobname;
import dev.galasa.zosbatch.ZosBatch;
import dev.galasa.zosbatch.ZosBatchJobname;

@Test
public class WebAppIntegrationDirect {
	
	@SimBank
	public ISimBank 		bank;
	@SimBankTerminal
	public ISimBankTerminal bankTerminal;
	@SimBankWebApp
	public ISimBankWebApp	webApp;
	
	@ZosImage(imageTag = "SIMBANK")
	public IZosImage        				image;
	@ZosBatch(imageTag="SIMBANK")
    public IZosBatch 						zosBatch;
    @ZosBatchJobname(imageTag="SIMBANK")
	public IZosBatchJobname 				zosBatchJobname;
    
	@SeleniumManager
	public ISeleniumManager seleniumManager;
	@BundleResources
	public IBundleResources resources;
	@HttpClient
	public IHttpClient      client;
	 
	@StoredArtifactRoot
	public Path             artifactRoot;
	@Logger
	public Log              logger;
	
	private final BigDecimal openingBalance = BigDecimal.valueOf(100.00);
	
	@Test
	public void webAppIntegrationTest() throws Exception {
		// Setup and initialisation steps to open a new account for use in this test
		String accountNumber = provisionAccount(openingBalance);
		String webpage = webApp.getHostName() + "/galasa-simplatform-webapp/simbank";
		BigDecimal creditAmount = BigDecimal.valueOf(355.17);		
		
		// Selenium Options to run the driver headlessly
		IFirefoxOptions options = seleniumManager.getFirefoxOptions();
        options.setHeadless(true);

        // Open the Simbank Web App in a Firefox browser
		IWebPage page = seleniumManager.allocateWebPage(webpage, options);
		page.maximize();
		page.takeScreenShot();
		assertThat(page.getTitle()).containsOnlyOnce("Simbank");
		
		// Fill in the Form and submit
		page.sendKeysToElementById("accnr", accountNumber);
		page.sendKeysToElementById("amount", creditAmount.toString());
		page.takeScreenShot();
		page.clickElementById("subb");
		
		// Report the result from the browser
		page.takeScreenShot();
		assertThat(page.findElementById("output").getText()).contains("Transaction complete");
		page.quit();
		
		// Perform Validation by checking the value has changed in the database through 3270 screens
		BigDecimal balance = retrieveAccountBalance(accountNumber);
		assertThat(balance).isEqualTo(openingBalance.add(creditAmount));
	}

	public String provisionAccount(BigDecimal openingBalance) throws Exception {
		// Generate a random account number
		String accountNumber =  generateRandomAccountNumber();
		boolean searching = true;
		
		// A looped search to ensure we find a unique account that hasn't been used.
		while (searching) {
			if (doesAccountExist(accountNumber)) {
				accountNumber =  generateRandomAccountNumber();
			} else {
				searching = false;
			}
		}
		
		// Open the account and give it an opening balance
		openAccount(accountNumber, openingBalance);
		
		return accountNumber;
	}
	
	public boolean doesAccountExist(String accountNumber) throws Exception {
		// Ensure the 3270 emulator is connected to the application
		if (!bankTerminal.isConnected()) {
			try {
				bankTerminal.connect();
			} catch (NetworkException e) {
				logger.error("Failed to connect to simbank", e);
				throw e;
			}
		}
		
		try {
			// Use the application to search for the account number
			bankTerminal.pf1()
				.waitForKeyboard()
				.positionCursorToFieldContaining("Account Number").tab()
				.type(accountNumber)
				.enter();
			
			String responseScreen = bankTerminal.waitForKeyboard().retrieveScreen();
			
			// Reset back to main menu
			bankTerminal.gotoMainMenu();
			
			// Return boolean response if the account exists 
			return responseScreen.contains("Account Found");
		} catch (Exception e) {
			logger.error("Failed to check account exists");
			throw e;
		}
	}
	
	public void openAccount(String accountNumber, BigDecimal openingBalance) throws Exception {
		// Use a batch Job to open the new account
		HashMap<String, Object> parameters = new HashMap<>();
        parameters.put("CONTROL", "ACCOUNT_OPEN");
		parameters.put("DATAIN", accountNumber+",20-24-09,"+openingBalance);	
		try {
			// Populate a Skeleton JCL file
			String jcl = resources.retrieveSkeletonFileAsString("/resources/skeletons/SIMBANK.jcl", parameters);
			IZosBatchJob batchJob = zosBatch.submitJob(jcl, zosBatchJobname);
		
			// Wait for the job to complete as we want to check the RC
			int rc = batchJob.waitForJob();
			if (rc != 0) {
				// Print the job output to the run log
				batchJob.retrieveOutput().forEach(jobOutput ->
					logger.info("batchJob.retrieveOutput(): " + jobOutput.getDdname() + "\n" + jobOutput.getRecords() + "\n")
				);
				Fail.fail("Batch job failed RETCODE=" + batchJob.getRetcode() + " Check batch job output");
				
			}
			logger.info("Batch job complete RETCODE=" + batchJob.getRetcode());
		} catch (Exception e) {
			logger.error("Failed to open account: " + accountNumber);
			throw e;
		}
	}
	
	public BigDecimal retrieveAccountBalance(String accountNumber) throws Exception {
		// Ensure the 3270 emulator is connected to the application
		if (!bankTerminal.isConnected()) {
			try {
				bankTerminal.connect();
			} catch (NetworkException e) {
				logger.error("Failed to connect to simbank", e);
				throw e;
			}
		}
		try {
			// Use the application to search for the account number
			bankTerminal.pf1()
				.waitForKeyboard()
				.positionCursorToFieldContaining("Account Number").tab()
				.type(accountNumber)
				.enter();
			
			// Retrieve the current balance of the account
			if (bankTerminal.waitForKeyboard().retrieveScreen().contains("Account Found")) {
				String balance = (bankTerminal.retrieveFieldTextAfterFieldWithString("Balance"));
				
				// Reset back to main menu
				bankTerminal.gotoMainMenu();
				return BigDecimal.valueOf(Double.valueOf(balance));
			} else {
				throw new Exception("Failed to find account");
			}
			
		} catch (Exception e) {
			logger.error("Failed to check account balance");
			throw e;
		}
	}
	
	// Generate a random 9 character Account number 
	public String generateRandomAccountNumber() {
		Random random = new Random();
		StringBuilder builder = new StringBuilder();
		
		for (int i=0;i<9;i++) {
			builder.append(random.nextInt(9));
		}
		return builder.toString();
	}
}
