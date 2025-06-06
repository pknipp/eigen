package com.heroku.java;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootApplication
@Controller
public class GettingStartedApplication {
    private final DataSource dataSource;

    @Autowired
    public GettingStartedApplication(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/")
    public String index(Model model) {
        float[][] exampleMatrix = {
                {1, 2, 4},
                {2, 3, 5},
                {4, 5, 6}
        };
        model.addAttribute("exampleMatrix", exampleMatrix);
        model.addAttribute("tstyle", "text-align: center; padding: 1px 8px;");
        model.addAttribute("baseUrl", "https://eigenvalue-vector-edd7507fb207.herokuapp.com/");
        model.addAttribute("frag", "(1),(2,3),(4,5,6)");
        return "index";
    }

    @RequestMapping("/api/{pathFragment}")
    public ResponseEntity<String> json(@PathVariable String pathFragment) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        HttpHeaders responseHeaders = new HttpHeaders();

        ArrayList<ArrayList<Float>> a = new ArrayList<>();
        String jsonString;
        String error = parseUrl(pathFragment, a);
        if (!error.isEmpty()) {
            jsonString = "{\"error\":" + error +  "}";
        } else {
            String aJson = objectMapper.writeValueAsString(a);
            int n = a.size();
            float[][] aClone = new float[n][n];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    aClone[i][j] = a.get(i).get(j);
                }
            }
            float[] d = new float[n];
            float[][] v = new float[n][n];
            error = jacobi(aClone, d, v);
            if (!error.equals("")) {
                jsonString = "{\"error\":" + error +  "}";
            } else {
                String dJson = objectMapper.writeValueAsString(d);
                String vJson = objectMapper.writeValueAsString(v);
                jsonString = "{\"matrix\": " + aJson + ", \"eigenvalues\": " + dJson + ", \"eigenvectors\": " + vJson + "}";
            }
        }
        return new ResponseEntity<String>(jsonString, responseHeaders, HttpStatus.CREATED);
    }

    @GetMapping("/{pathFragment}")
    public String diagonalize(@PathVariable String pathFragment, Model model) {
        if (!pathFragment.equals("favicon.ico")) {
            ArrayList<ArrayList<Float>> a = new ArrayList<>();
            String error = parseUrl(pathFragment, a);
            if (!error.isEmpty()) {
                model.addAttribute("error", error);
                return "error";
            } else {
                int n = a.size();
                float[][] aClone = new float[n][n];
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < n; j++) {
                        aClone[i][j] = a.get(i).get(j);
                    }
                }
                model.addAttribute("matrix", a);
                float[] d = new float[n];
                float[][] v = new float[n][n];
                error = jacobi(aClone, d, v);
                if (!error.equals("")) {
                    model.addAttribute("error", error);
                    return "error";
                }
                model.addAttribute("eigenvalues", d);
                model.addAttribute("eigenvectors", v);
                model.addAttribute("n", n);
                model.addAttribute("tstyle", "text-align: center; padding: 1px 8px;");
            }
        }
        return "result";
    }

    public String parseUrl(String pathFragment, ArrayList<ArrayList<Float>> a) {
        String openParen = "'('";
        String closeParen = "')'";
        String thisChar = pathFragment.substring(0, 1);
        if (!("'" + thisChar + "'").equals(openParen)) {
            return "The first character in the path fragment should be " + openParen + " not '" + thisChar + "'.";
        }
        pathFragment = pathFragment.substring(1);
        thisChar = pathFragment.substring(pathFragment.length() - 1);
        if (!("'" + thisChar + "'").equals(closeParen)) {
            return "The last character in the url should be " + closeParen + " not '" + thisChar + "'.";
        }
        pathFragment = pathFragment.substring(0, pathFragment.length() - 1);
        String[] pathArr = pathFragment.split("\\),\\(");
        int n = pathArr.length;
        for (int i = 0; i < n; i++) {
            a.add(new ArrayList<Float>());
        }
        for (int i = 0; i < n; i++) {
            String col = pathArr[i];
            String[] colArr = col.split(",");
            if (colArr.length != i + 1) {
                return "The contents of the " + (i + 1) + "-th set of parentheses " + col + " should include " + i + " commas, not " + (colArr.length - 1) + ".";
            }
            for (int j = 0; j <= i; j++) {
                float val;
                try {
                    val = Float.parseFloat(colArr[j]);
                    a.get(i).add(val);
                    if (i != j) a.get(j).add(val);
                } catch (NumberFormatException e) {
                    return "The string \"" + colArr[j] + "\" cannot be parsed as a float.";
                }
            }
        }
        return "";
    }

    public String jacobi(float[][] a, float[] d, float[][] v) {
        // Computes all eigenvalues and eigenvectors of a real symmetric matrix a, which is of size n by n, stored in a physical np by np array. On output, elements of a above the diagonal are
        // destroyed. d returns the eigenvalues of a in its first n elements. v is a matrix with the same
        // logical and physical dimensions as a, whose columns contain, on output, the normalized
        // eigenvectors of a. nrot returns the number of Jacobi rotations that were required.
        // This code was translated from Fortran source code in Chapter 11 of "Numerical Recipes" book.
        int n = a.length;
        float[] b = new float[n];
        float[] z = new float[n];
        for (int ip = 0; ip < n; ip++) {
            v[ip][ip] = 1;
            b[ip] = a[ip][ip];
            d[ip] = b[ip];
            z[ip] = 0;
        }
        int nrot = 0;
        for (int i = 0; i < 50; i++) {
            float sm = 0;
            for (int ip = 0; ip < n - 1; ip++) {
                for (int iq = ip + 1; iq < n; iq++) {
                    sm += Math.abs(a[ip][iq]);
                }
            }
            if (sm == 0) {
                // Sort eigenvalues from smallest to largest.
                for (int l = 0; l < n - 2; l++) {
                    int k = l;
                    float p = d[l];
                    for (int j = l + 1; j < n; j++) {
                        if (d[j] <= p) {
                            k = j;
                            p = d[j];
                        }
                    }
                    if (k != l) {
                        d[k] = d[l];
                        d[l] = p;
                        for (int j = l; j < n; j++) {
                            p = v[j][l];
                            v[j][l] = v[j][k];
                            v[j][k] = p;
                        }
                    }
                }
                return "";
            }
            double tresh;
            if (i < 4) {
                tresh = 0.2 * sm / Math.pow(n, 2);
            } else {
                tresh = 0;
            }
            for (int ip = 0; ip < n - 1; ip++) {
                for (int iq = ip + 1; iq < n; iq++) {
                    float g = 100 * Math.abs(a[ip][iq]);
//                  After four sweeps, skip the rotation if the off-diagonal element is small.
                    if (i > 3 && (Math.abs(d[ip]) + g == Math.abs(d[ip])) && (Math.abs(d[iq]) + g == Math.abs(d[iq]))) {
                        a[ip][iq] = 0;
                    } else if (Math.abs(a[ip][iq]) > tresh) {
                        float h = d[iq] - d[ip];
                        float t;
                        if (Math.abs(h) + g == Math.abs(h)) {
                            t = a[ip][iq] / h;
                        } else {
                            float theta = (float) 0.5 * h / a[ip][iq];
                            t =  1 / (float) (Math.abs(theta) + Math.sqrt(1 + Math.pow(theta, 2)));
                            if (theta < 0) t = -t;
                        }
                        float c = 1 / (float) Math.sqrt(1 + Math.pow(t, 2));
                        float s = t * c;
                        float tau = s / (1 + c);
                        h = t * a[ip][iq];
                        z[ip] -= h;
                        z[iq] += h;
                        d[ip] -= h;
                        d[iq] += h;
                        a[ip][iq] = 0;
                        for (int j = 0; j < ip; j++) {
                            g = a[j][ip];
                            h = a[j][iq];
                            a[j][ip] = g - s * (h + g * tau);
                            a[j][iq] = h + s * (g - h * tau);
                        }
                        for (int j = ip + 1; j < iq; j++) {
                            g = a[ip][j];
                            h = a[j][iq];
                            a[ip][j] = g - s * (h + g * tau);
                            a[j][iq] = h + s * (g - h * tau);
                        }
                        for (int j = iq + 1; j < n; j++) {
                            g = a[ip][j];
                            h = a[iq][j];
                            a[ip][j] = g - s * (h + g * tau);
                            a[iq][j] = h + s * (g - h * tau);
                        }
                        for (int j = 0; j < n; j++) {
                            g = v[j][ip];
                            h = v[j][iq];
                            v[j][ip] = g - s * (h + g * tau);
                            v[j][iq] = h + s * (g - h * tau);
                        }
                        nrot++;
                    }
                }
            }
            for (int ip= 0; ip < n; ip++) {
                b[ip] += z[ip];
                d[ip] = b[ip];
                z[ip] = 0;
            }
        }
        return "The Jacobi method did not converge after 50 iterations.";
    }

    @GetMapping("/database")
    String database(Map<String, Object> model) {
        try (Connection connection = dataSource.getConnection()) {
            final var statement = connection.createStatement();
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS ticks (tick timestamp)");
            statement.executeUpdate("INSERT INTO ticks VALUES (now())");

            final var resultSet = statement.executeQuery("SELECT tick FROM ticks");
            final var output = new ArrayList<>();
            while (resultSet.next()) {
                output.add("Read from DB: " + resultSet.getTimestamp("tick"));
            }

            model.put("records", output);
            return "database";

        } catch (Throwable t) {
            model.put("message", t.getMessage());
            return "error";
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(GettingStartedApplication.class, args);
    }
}
