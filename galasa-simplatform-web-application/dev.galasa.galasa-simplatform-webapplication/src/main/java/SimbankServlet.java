

import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;

import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("")
public class SimbankServlet extends HttpServlet{
	
	public void doGet(HttpServletResponse res, HttpServletRequest req) {
		try {
			res.setContentType("text/html");
			res.getWriter().println("<h1>This is simbank</h1>");
//			RequestDispatcher rd = req.getRequestDispatcher("index.html");
//			rd.forward(req, res);
		} catch(Exception e){
			e.printStackTrace();
		}
		
	}
	
	public void doPost(String[] args) {
		
		
		
	}
	
}
