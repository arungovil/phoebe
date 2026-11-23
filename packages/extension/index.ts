import { WebSocket, WebSocketServer } from "ws";
import { Bonjour } from "bonjour-service";

type Pi = {
  on(event: "session_start" | "session_shutdown", handler: () => void): void;
  on(event: "agent_settled", handler: (event: { aborted: boolean }) => void): void;
};

export default function (pi: Pi) {
  let server: WebSocketServer | undefined;
  let bonjour: Bonjour | undefined;

  pi.on("session_start", () => {
    const port = Number(process.env.PHOEBE_PORT || 48371);
    server = new WebSocketServer({ host: "0.0.0.0", port, path: "/ws" });
    server.on("error", (error) => console.error("Phoebe: WebSocket server error:", error.message));
    const activeServer = server;
    server.on("listening", () => {
      if (server !== activeServer) return;
      try {
        bonjour = new Bonjour();
        bonjour.publish({ name: `Phoebe on ${process.pid}`, type: "phoebe", port });
      } catch (error) {
        console.error("Phoebe: could not advertise server:", error);
      }
    });
  });

  pi.on("agent_settled", (event) => {
    const message = JSON.stringify({ type: "agent_settled", aborted: event.aborted });
    for (const client of server?.clients ?? []) {
      if (client.readyState === WebSocket.OPEN) {
        client.send(message, (error) => {
          if (error) console.error("Phoebe: could not send event:", error.message);
        });
      }
    }
  });

  pi.on("session_shutdown", () => {
    bonjour?.destroy();
    bonjour = undefined;
    if (!server) return;
    for (const client of server.clients) client.terminate();
    server.close();
    server = undefined;
  });
}
