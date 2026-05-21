**1. Ideia completa do MVP**

A aplicação pode ser chamada **Copa Ticketing MVP** e terá um fluxo simples de venda de ingressos por **partida + setor**, com mapa de assentos individual. Isso mantém o projeto viável em pouco tempo, mas ainda demonstra os pontos técnicos mais importantes: catálogo, inventário, reserva temporária, checkout, pagamento Pix simulado, emissão de tickets e painel operacional.

A ideia central é:

O usuário escolhe uma partida, seleciona um setor e um assento ( Com Mapa ) e quantidade de ingressos, cria uma reserva temporária, realiza um checkout com Pix simulado e recebe tickets digitais após a confirmação do pagamento.

O MVP não precisa ter login completo, senha, gateway real de pagamento, antifraude complexo ou mapa de assentos. Para a demonstração, basta identificar o comprador por 
**nome, e-mail e documento**.

---

# **2. Escopo funcional do MVP**

## **Funcionalidades principais**

### **Cliente**

1. Visualizar catálogo de partidas.
2. Filtrar partidas por cidade, data ou seleção.
3. Visualizar setores disponíveis por partida.
4. Selecionar setor, assento e quantidade.
5. Criar uma reserva temporária com expiração.
6. Receber recomendações caso o setor desejado esteja indisponível.
7. Fazer checkout com Pix simulado.
8. Confirmar pagamento via ação simulada.
9. Visualizar tickets digitais emitidos.

⠀
### **Operação/Admin**

1. Ver painel operacional com vendas, reservas ativas, receita e ocupação por partida.
2. Ver pedidos recentes.
3. Ver estoque por partida e setor.
4. Ver eventos de risco simples no checkout.