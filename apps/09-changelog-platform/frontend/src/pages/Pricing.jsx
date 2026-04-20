import { useState } from 'react';
import { monetizationApi } from '../api/client';
import { Check } from 'lucide-react';

const PLANS = [
  {
    name: 'Starter',
    price: '$0',
    priceId: 'price_starter_free',
    description: 'Perfect for small side projects',
    features: ['1 Project', '100 Posts/month', 'Public Changelog'],
  },
  {
    name: 'Pro',
    price: '$49',
    priceId: 'price_pro_monthly',
    description: 'For growing SaaS teams',
    features: [
      'Unlimited Projects',
      'AI Rewrite Tool',
      'Custom Domain',
      'Advanced Analytics',
    ],
    highlight: true,
  },
  {
    name: 'Enterprise',
    price: '$199',
    priceId: 'price_enterprise_monthly',
    description: 'Full business operations platform',
    features: [
      'Everything in Pro',
      'Churn Prediction',
      'Drip Campaigns',
      'Dedicated Support',
    ],
  },
];

export function Pricing() {
  const [loading, setLoading] = useState(null);

  const handleSubscribe = async (plan) => {
    if (plan.price === '$0') {
      alert('Free plan activated!');
      return;
    }

    setLoading(plan.priceId);
    try {
      const tenantId = '00000000-0000-0000-0000-000000000000'; // Replace with real tenant ID
      const customerId = '00000000-0000-0000-0000-000000000001'; // Replace with real customer ID

      const response = await monetizationApi.createCheckoutSession({
        tenantId,
        customerId,
        priceId: plan.priceId,
        successUrl: window.location.origin + '/billing/success',
        cancelUrl: window.location.origin + '/pricing',
      });

      // Redirect to Stripe Checkout
      window.location.href = response.data.checkoutUrl;
    } catch (error) {
      console.error('Checkout failed:', error);
      alert('Failed to initiate checkout. Please check your Stripe configuration.');
    } finally {
      setLoading(null);
    }
  };

  return (
    <div className="py-12 px-4 max-w-7xl mx-auto">
      <div className="text-center mb-16">
        <h2 className="text-4xl font-extrabold text-gray-900 sm:text-5xl">
          Simple, Transparent Pricing
        </h2>
        <p className="mt-4 text-xl text-gray-600">
          Scale your SaaS operations with our integrated platform.
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
        {PLANS.map((plan) => (
          <div
            key={plan.name}
            className={`relative p-8 bg-white border rounded-2xl shadow-sm flex flex-col ${
              plan.highlight ? 'border-blue-500 ring-4 ring-blue-500 ring-opacity-10' : 'border-gray-200'
            }`}
          >
            {plan.highlight && (
              <div className="absolute top-0 right-0 -translate-y-1/2 translate-x-1/4 bg-blue-500 text-white text-xs font-bold px-3 py-1 rounded-full uppercase tracking-wider">
                Most Popular
              </div>
            )}
            <div className="mb-8">
              <h3 className="text-xl font-bold text-gray-900">{plan.name}</h3>
              <p className="text-gray-500 mt-2 text-sm">{plan.description}</p>
              <div className="mt-4 flex items-baseline">
                <span className="text-4xl font-extrabold text-gray-900">{plan.price}</span>
                <span className="ml-1 text-xl font-medium text-gray-500">/mo</span>
              </div>
            </div>

            <ul className="space-y-4 mb-8 flex-1">
              {plan.features.map((feature) => (
                <li key={feature} className="flex items-start">
                  <Check className="h-5 w-5 text-green-500 shrink-0 mr-3" />
                  <span className="text-gray-600 text-sm">{feature}</span>
                </li>
              ))}
            </ul>

            <button
              onClick={() => handleSubscribe(plan)}
              disabled={loading !== null}
              className={`w-full py-3 px-6 rounded-xl font-bold transition ${
                plan.highlight
                  ? 'bg-blue-600 text-white hover:bg-blue-700 shadow-lg shadow-blue-200'
                  : 'bg-gray-50 text-gray-900 hover:bg-gray-100'
              } disabled:opacity-50`}
            >
              {loading === plan.priceId ? 'Processing...' : `Get Started with ${plan.name}`}
            </button>
          </div>
        ))}
      </div>
    </div>
  );
}
